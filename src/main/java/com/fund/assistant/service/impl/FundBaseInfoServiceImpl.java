package com.fund.assistant.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.dto.FundQueryDTO;
import com.fund.assistant.entity.FundBaseInfo;
import com.fund.assistant.entity.FundNetValueHistory;
import com.fund.assistant.entity.FundStockHoldDetail;
import com.fund.assistant.entity.StockBaseInfo;
import com.fund.assistant.exception.BusinessException;
import com.fund.assistant.mapper.FundBaseInfoMapper;
import com.fund.assistant.mapper.FundNetValueHistoryMapper;
import com.fund.assistant.mapper.FundStockHoldDetailMapper;
import com.fund.assistant.mapper.StockBaseInfoMapper;
import com.fund.assistant.service.FundBaseInfoService;
import com.fund.assistant.service.FundStockHoldDetailService;
import com.fund.assistant.vo.FundBaseInfoVO;
import com.fund.assistant.vo.FundNetValueVO;
import com.fund.assistant.vo.FundRealtimeValuationVO;
import com.fund.assistant.vo.FundStockHoldVO;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FundBaseInfoServiceImpl extends ServiceImpl<FundBaseInfoMapper, FundBaseInfo> implements FundBaseInfoService {

    @Autowired
    private FundNetValueHistoryMapper fundNetValueHistoryMapper;

    @Autowired
    private FundStockHoldDetailMapper fundStockHoldDetailMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private FundStockHoldDetailService fundStockHoldDetailService;

    @Autowired
    private StockBaseInfoMapper stockBaseInfoMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final long NET_VALUE_CACHE_TTL = 7;

    /**
     * 分页查询基金列表（支持搜索）
     */
    @Override
    public IPage<FundBaseInfoVO> getFundList(FundQueryDTO dto) {
        // 无搜索关键词时，优先从 Redis 全量缓存取
        String keyword = dto.getKeyword();
        if (!StringUtils.hasText(keyword) && !StringUtils.hasText(dto.getFundType()) && !StringUtils.hasText(dto.getFundPlate())) {
            String allCache = null;
            try { allCache = redisTemplate.opsForValue().get("fund:all:list"); } catch (Exception ignored) {}
            if (allCache != null) {
                List<FundBaseInfoVO> allList = JSON.parseArray(allCache, FundBaseInfoVO.class);
                int start = (dto.getPageNum() - 1) * dto.getPageSize();
                int end = Math.min(start + dto.getPageSize(), allList.size());
                List<FundBaseInfoVO> pageList = start < allList.size() ? allList.subList(start, end) : List.of();
                IPage<FundBaseInfoVO> voPage = new Page<>(dto.getPageNum(), dto.getPageSize(), allList.size());
                voPage.setRecords(pageList);
                return voPage;
            }
        }

        // 1. 构建分页对象
        Page<FundBaseInfo> page = new Page<>(dto.getPageNum(), dto.getPageSize());

        // 2. 构建查询条件
        LambdaQueryWrapper<FundBaseInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(FundBaseInfo::getFundName, keyword)
                    .or()
                    .like(FundBaseInfo::getFundShortName, keyword)
                    .or()
                    .eq(FundBaseInfo::getFundCode, keyword)
            );
        }
        if (StringUtils.hasText(dto.getFundType())) {
            wrapper.eq(FundBaseInfo::getFundType, dto.getFundType());
        }
        if (StringUtils.hasText(dto.getFundPlate())) {
            wrapper.eq(FundBaseInfo::getFundPlate, dto.getFundPlate());
        }
        wrapper.orderByDesc(FundBaseInfo::getUpdateTime);

        // 3. 执行分页查询
        IPage<FundBaseInfo> fundPage = this.page(page, wrapper);

        // 4. 按基金代码搜索时，本地没有就自动同步
        if (fundPage.getTotal() == 0 && keyword != null && keyword.matches("\\d{6}")) {
            log.info("本地未找到基金 {}，尝试从外部接口同步", keyword);
            try {
                syncFundBaseInfo(keyword);
                fundPage = this.page(page, wrapper);
            } catch (Exception e) {
                log.warn("外部接口同步失败：{}", e.getMessage());
            }
        }

        // 5. 转换为VO
        IPage<FundBaseInfoVO> voPage = fundPage.convert(fund -> {
            FundBaseInfoVO vo = new FundBaseInfoVO();
            BeanUtils.copyProperties(fund, vo);
            return vo;
        });

        // 6. 全量缓存（无筛选条件时），后续分页查询直接走 Redis
        if (!StringUtils.hasText(keyword) && !StringUtils.hasText(dto.getFundType()) && !StringUtils.hasText(dto.getFundPlate())) {
            try {
                redisTemplate.opsForValue().set("fund:all:list",
                        JSON.toJSONString(voPage.getRecords()),
                        1, TimeUnit.DAYS);
            } catch (Exception ignored) {}
        }

        return voPage;
    }

    /**
     * 根据基金代码查询基金详情
     */
    @Override
    public FundBaseInfoVO getFundDetail(String fundCode) {
        // 优先查 Redis 缓存（只缓存静态元数据，净值等动态字段每次都查最新）
        String cacheKey = "fund:info:" + fundCode;
        FundBaseInfo fund = null;
        boolean fromCache = false;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                fund = JSON.parseObject(cached, FundBaseInfo.class);
                fromCache = true;
            }
        } catch (Exception e) {
            log.warn("读取基金详情缓存失败：{}", fundCode);
        }

        if (fund == null) {
            LambdaQueryWrapper<FundBaseInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(FundBaseInfo::getFundCode, fundCode);
            fund = this.getOne(wrapper);
        }

        if (fund == null) {
            // 本地没有，自动从外部接口同步
            log.info("基金 {} 本地不存在，尝试从外部接口同步", fundCode);
            fund = syncFundBaseInfo(fundCode);
        }

        // 如果元数据缺失，自动补全
        if (!StringUtils.hasText(fund.getFundType()) || !StringUtils.hasText(fund.getFundManager()) || !StringUtils.hasText(fund.getFundCompany())) {
            try {
                fund = syncFundBaseInfo(fundCode);
                fromCache = false;
            } catch (Exception e) {
                log.warn("自动补全基金元数据失败：{}", fundCode);
            }
        }

        FundBaseInfoVO vo = new FundBaseInfoVO();
        BeanUtils.copyProperties(fund, vo);

        // 最新净值：优先 Redis ZSet，再 MySQL
        FundNetValueVO latestNavVo = null;
        try {
            Set<String> zsetEntries = redisTemplate.opsForZSet()
                    .reverseRange("net_value:" + fundCode, 0, 0); // 取最新一条
            if (zsetEntries != null && !zsetEntries.isEmpty()) {
                latestNavVo = JSON.parseObject(zsetEntries.iterator().next(), FundNetValueVO.class);
            }
        } catch (Exception e) {
            log.warn("读取净值ZSet缓存失败：{}", fundCode);
        }
        if (latestNavVo == null) {
            FundNetValueHistory latestNav = fundNetValueHistoryMapper.selectOne(
                    new LambdaQueryWrapper<FundNetValueHistory>()
                            .eq(FundNetValueHistory::getFundCode, fundCode)
                            .orderByDesc(FundNetValueHistory::getNetValueDate)
                            .last("LIMIT 1"));
            if (latestNav != null) {
                latestNavVo = new FundNetValueVO();
                BeanUtils.copyProperties(latestNav, latestNavVo);
            }
        }
        if (latestNavVo != null) {
            vo.setLatestNetValue(latestNavVo.getUnitNetValue());
            vo.setLatestChangeRate(latestNavVo.getDailyChangeRate());
            vo.setNetValueDate(latestNavVo.getNetValueDate());
        }

        // 写入 Redis（只缓存静态元数据，不含净值动态字段，如非来自缓存则更新）
        if (!fromCache) {
            try {
                // 清除动态字段后再缓存，避免过时净值被缓存
                FundBaseInfo cacheCopy = new FundBaseInfo();
                BeanUtils.copyProperties(fund, cacheCopy);
                cacheCopy.setLatestNetValue(null);
                cacheCopy.setLatestChangeRate(null);
                redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(cacheCopy), 7, TimeUnit.DAYS);
            } catch (Exception e) {
                log.warn("写入基金详情缓存失败：{}", fundCode);
            }
        }

        return vo;
    }

    /**
     * 查询基金历史净值（Redis ZSet 缓存 + MySQL + 天天基金 API 三级兜底）
     */
    @Override
    public List<FundNetValueVO> getFundNetValueHistory(String fundCode, LocalDate startDate, LocalDate endDate) {
        String redisKey = "net_value:" + fundCode;

        // 1. 先查 Redis ZSet 缓存
        try {
            // 获取ZSet中所有缓存的净值数据
            Set<String> cached = redisTemplate.opsForZSet().range(redisKey, 0, -1);
            if (cached != null && !cached.isEmpty()) {
                //把缓存的JSON字符串批量反序列化为VO对象
                List<FundNetValueVO> allList = new ArrayList<>();
                for (String json : cached) {
                    allList.add(JSON.parseObject(json, FundNetValueVO.class));
                }

                // 如果 startDate 和 endDate 都是 null，直接返回全部
                if (startDate == null && endDate == null) {
                    allList.sort((a, b) -> b.getNetValueDate().compareTo(a.getNetValueDate()));
                    return allList;
                }

                // 按日期范围过滤
                LocalDate begin = startDate != null ? startDate : LocalDate.now().minusMonths(1);
                LocalDate end = endDate != null ? endDate : LocalDate.now();
                return allList.stream()
                        .filter(vo -> {
                            LocalDate d = vo.getNetValueDate();
                            return d != null && !d.isBefore(begin) && !d.isAfter(end);
                        })
                        .sorted((a, b) -> b.getNetValueDate().compareTo(a.getNetValueDate()))
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("读取净值 ZSet 缓存失败，fundCode：{}，{}", fundCode, e.getMessage());
        }

        // 2. 缓存未命中，走 MySQL
        LocalDate beginDate = startDate != null ? startDate : LocalDate.now().minusMonths(1);
        LocalDate endDateFinal = endDate != null ? endDate : LocalDate.now();

        LambdaQueryWrapper<FundNetValueHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundNetValueHistory::getFundCode, fundCode)
                .ge(FundNetValueHistory::getNetValueDate, beginDate)
                .le(FundNetValueHistory::getNetValueDate, endDateFinal)
                .orderByDesc(FundNetValueHistory::getNetValueDate);

        List<FundNetValueHistory> dbList = fundNetValueHistoryMapper.selectList(wrapper);

        if (!dbList.isEmpty()) {
            // MySQL 有数据，逐条回填 Redis ZSet
            try {
                for (FundNetValueHistory record : dbList) {
                    FundNetValueVO vo = new FundNetValueVO();
                    BeanUtils.copyProperties(record, vo);
                    String member = JSON.toJSONString(vo);
                    double score = record.getNetValueDate().toEpochDay();
                    redisTemplate.opsForZSet().add(redisKey, member, score);
                }

            } catch (Exception e) {
                log.warn("写入净值 ZSet 缓存失败，fundCode：{}，{}", fundCode, e.getMessage());
            }

            return dbList.stream().map(history -> {
                FundNetValueVO vo = new FundNetValueVO();
                BeanUtils.copyProperties(history, vo);
                return vo;
            }).collect(Collectors.toList());
        }

        // 3. MySQL 也没有，调东方财富接口拉取
        List<FundNetValueHistory> apiList = fetchNetValueHistoryFromEastMoney(fundCode);
        if (apiList.isEmpty()) {
            return Collections.emptyList();
        }

        // 回填 Redis ZSet
        try {
            for (FundNetValueHistory record : apiList) {
                FundNetValueVO vo = new FundNetValueVO();
                BeanUtils.copyProperties(record, vo);
                String member = JSON.toJSONString(vo);
                double score = record.getNetValueDate().toEpochDay();
                redisTemplate.opsForZSet().add(redisKey, member, score);
            }
        } catch (Exception e) {
            log.warn("写入净值 ZSet 缓存失败，fundCode：{}，{}", fundCode, e.getMessage());
        }

        // 按范围过滤返回
        return apiList.stream()
                .filter(r -> !r.getNetValueDate().isBefore(beginDate) && !r.getNetValueDate().isAfter(endDateFinal))
                .map(history -> {
                    FundNetValueVO vo = new FundNetValueVO();
                    BeanUtils.copyProperties(history, vo);
                    return vo;
                }).collect(Collectors.toList());
    }

    /**
     * 查询基金前十重仓股
     */
    @Override
    public List<FundStockHoldVO> getFundStockHold(String fundCode) {
        // 1. 先查数据库最新的重仓股数据
        LambdaQueryWrapper<FundStockHoldDetail> dateWrapper = new LambdaQueryWrapper<>();
        dateWrapper.select(FundStockHoldDetail::getReportDate)
                .eq(FundStockHoldDetail::getFundCode, fundCode)
                .orderByDesc(FundStockHoldDetail::getReportDate)
                .last("LIMIT 1");
        FundStockHoldDetail latest = fundStockHoldDetailMapper.selectOne(dateWrapper);

        // 2. 判断是否需要更新
        boolean needUpdate = false;
        if (latest == null) {
            // 数据库里没有，必须爬取
            needUpdate = true;
        } else {
            // 有数据，检查是否超过10天
            LocalDate latestDate = latest.getReportDate() != null ? latest.getReportDate() : LocalDate.MIN;
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(latestDate, LocalDate.now());
            if (daysBetween >= 10) {
                needUpdate = true;
            }
        }

        // 3. 需要更新，执行爬取
        if (needUpdate) {
            log.info("基金 {} 重仓股数据需要更新，开始爬取天天基金", fundCode);
            List<FundStockHoldDetail> newHoldList = crawlFundHoldFromEastMoney(fundCode);
            if (newHoldList != null && !newHoldList.isEmpty()) {
                // 先删除旧数据，再插入新数据
                LambdaQueryWrapper<FundStockHoldDetail> deleteWrapper = new LambdaQueryWrapper<>();
                deleteWrapper.eq(FundStockHoldDetail::getFundCode, fundCode);
                fundStockHoldDetailMapper.delete(deleteWrapper);

                // 批量插入新数据
                fundStockHoldDetailService.saveBatch(newHoldList);
                log.info("基金 {} 重仓股数据更新成功，共 {} 只", fundCode, newHoldList.size());
            }
        }

        // 4. 不管有没有更新，都从数据库查最新数据返回
        // 重新查最新报告日期
        LambdaQueryWrapper<FundStockHoldDetail> finalDateWrapper = new LambdaQueryWrapper<>();
        finalDateWrapper.select(FundStockHoldDetail::getReportDate)
                .eq(FundStockHoldDetail::getFundCode, fundCode)
                .orderByDesc(FundStockHoldDetail::getReportDate)
                .last("LIMIT 1");
        FundStockHoldDetail finalLatest = fundStockHoldDetailMapper.selectOne(finalDateWrapper);
        if (finalLatest == null) {
            return Collections.emptyList();
        }
        LocalDate finalLatestDate = finalLatest.getReportDate();

        // 按最新报告日期过滤，按持仓占比倒序
        LambdaQueryWrapper<FundStockHoldDetail> finalWrapper = new LambdaQueryWrapper<>();
        finalWrapper.eq(FundStockHoldDetail::getFundCode, fundCode)
                .eq(FundStockHoldDetail::getReportDate, finalLatestDate)
                .orderByDesc(FundStockHoldDetail::getHoldRatio)
                .last("LIMIT 10");

        List<FundStockHoldDetail> list = fundStockHoldDetailMapper.selectList(finalWrapper);
        return list.stream().map(stock -> {
            FundStockHoldVO vo = new FundStockHoldVO();
            BeanUtils.copyProperties(stock, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 爬取天天基金重仓股数据
     */
    private List<FundStockHoldDetail> crawlFundHoldFromEastMoney(String fundCode) {
        try {
            String url = "https://fundf10.eastmoney.com/FundArchivesDatas.aspx"
                    + "?type=jjcc&code=" + fundCode + "&topline=10";

            Document doc = Jsoup.connect(url)
                    .header("Referer", "https://fundf10.eastmoney.com/")
                    .ignoreContentType(true)
                    .timeout(8000)
                    .get();

            // 找表格行
            Elements tables = doc.select("table");
            Element targetTable = null;
            for (Element t : tables) {
                if (t.select("tr").size() >= 3) { targetTable = t; break; }
            }
            if (targetTable == null) {
                log.warn("基金 {} 重仓股页面未找到数据表格", fundCode);
                return Collections.emptyList();
            }

            Elements rows = targetTable.select("tr");
            List<FundStockHoldDetail> holdList = new ArrayList<>();
            for (int i = 1; i < rows.size(); i++) {
                Elements tds = rows.get(i).select("td");
                if (tds.size() < 7) continue;
                try {
                    String stockCode = tds.get(1).text().trim();
                    String stockName = tds.get(2).text().trim();
                    String ratioStr = tds.get(6).text().replace("%", "").trim();

                    if (stockCode.isEmpty() || stockName.isEmpty() || ratioStr.isEmpty()) continue;

                    syncStockBaseInfo(stockCode, stockName);

                    FundStockHoldDetail hold = FundStockHoldDetail.builder()
                            .fundCode(fundCode)
                            .stockCode(stockCode)
                            .stockName(stockName)
                            .holdRatio(new BigDecimal(ratioStr))
                            .reportDate(LocalDate.now())
                            .createTime(LocalDateTime.now())
                            .updateTime(LocalDateTime.now())
                            .build();
                    holdList.add(hold);
                } catch (Exception e) {
                    log.warn("单条解析失败：{}", e.getMessage());
                }
            }

            log.info("基金 {} 成功解析到 {} 只重仓股", fundCode, holdList.size());
            return holdList;

        } catch (Exception e) {
            log.error("解析失败：{}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 自动同步股票基础信息（如果不存在则插入，存在则跳过）
     */
    private void syncStockBaseInfo(String stockCode, String stockName) {
        try {
            // 先查是否已存在
            LambdaQueryWrapper<StockBaseInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(StockBaseInfo::getStockCode, stockCode);
            StockBaseInfo exist = stockBaseInfoMapper.selectOne(wrapper);

            if (exist != null) {
                // 已存在，跳过
                return;
            }

            // 不存在，插入
            StockBaseInfo stock = new StockBaseInfo();
            stock.setStockCode(stockCode);
            stock.setStockName(stockName);

            // 判断市场：6开头=上交所(SH)，0/3开头=深交所(SZ)
            if (stockCode.startsWith("6")) {
                stock.setStockMarket("SH");
            } else if (stockCode.startsWith("0") || stockCode.startsWith("3")) {
                stock.setStockMarket("SZ");
            } else {
                stock.setStockMarket("UNKNOWN");
            }

            stock.setCreateTime(LocalDateTime.now());
            stock.setUpdateTime(LocalDateTime.now());
            stockBaseInfoMapper.insert(stock);
            log.info("股票基础信息同步成功：{} - {}", stockCode, stockName);
        } catch (Exception e) {
            log.warn("股票基础信息同步失败：{}", stockCode);
        }
    }

    /**
     * 拉取天天基金历史净值（MySQL + Redis 均无数据时兜底）
     */
    private List<FundNetValueHistory> fetchNetValueHistoryFromEastMoney(String fundCode) {
        try {
            int pageSize = 100;
            java.util.Set<String> seenDates = new java.util.HashSet<>();
            List<FundNetValueHistory> list = new ArrayList<>();
            // 多页拉取直到无新数据
            for (int page = 1; page <= 50; page++) {
                String url = String.format("https://api.fund.eastmoney.com/f10/lsjz?fundCode=%s&pageIndex=%d&pageSize=%d", fundCode, page, pageSize);
                String result = cn.hutool.http.HttpUtil.createGet(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Referer", "http://fund.eastmoney.com/")
                        .timeout(5000)
                        .execute()
                        .body();
                com.alibaba.fastjson.JSONObject json = JSON.parseObject(result);
                com.alibaba.fastjson.JSONObject data = json.getJSONObject("Data");
                if (data == null) break;
                com.alibaba.fastjson.JSONArray lsjzList = data.getJSONArray("LSJZList");
                if (lsjzList == null || lsjzList.isEmpty()) break;

                int pageCount = 0;
                for (int i = 0; i < lsjzList.size(); i++) {
                    com.alibaba.fastjson.JSONObject item = lsjzList.getJSONObject(i);
                    String dateStr = item.getString("FSRQ");
                    String unitNav = item.getString("DWJZ");
                    if (dateStr == null || unitNav == null || !StringUtils.hasText(unitNav)) continue;
                    String key = fundCode + "_" + dateStr;
                    if (seenDates.contains(key)) continue;
                    seenDates.add(key);
                    String cumulativeNav = item.getString("LJJZ");
                    String changeRate = item.getString("JZZZL");
                    FundNetValueHistory history = FundNetValueHistory.builder()
                            .fundCode(fundCode).netValueDate(LocalDate.parse(dateStr))
                            .unitNetValue(new BigDecimal(unitNav))
                            .cumulativeNetValue(StringUtils.hasText(cumulativeNav) ? new BigDecimal(cumulativeNav) : null)
                            .dailyChangeRate(StringUtils.hasText(changeRate) ? new BigDecimal(changeRate) : null)
                            .createTime(LocalDateTime.now()).build();
                    list.add(history);
                    pageCount++;
                }
                if (pageCount == 0) break; // 当前页无数据，停止翻页
            }

            if (list.isEmpty()) {
                return Collections.emptyList();
            }

            // 批量写入 MySQL（去重，跳过已存在的日期）
            List<FundNetValueHistory> existingList = fundNetValueHistoryMapper.selectList(
                    new LambdaQueryWrapper<FundNetValueHistory>()
                            .eq(FundNetValueHistory::getFundCode, fundCode)
                            .select(FundNetValueHistory::getNetValueDate));
            java.util.Set<LocalDate> existingDates = existingList.stream()
                    .map(FundNetValueHistory::getNetValueDate)
                    .collect(Collectors.toSet());

            for (FundNetValueHistory record : list) {
                if (!existingDates.contains(record.getNetValueDate())) {
                    fundNetValueHistoryMapper.insert(record);
                }
            }

            log.info("基金 {} 历史净值拉取成功，共 {} 条（新增 {} 条）", fundCode, list.size(),
                    list.size() - existingDates.size());
            return list;

        } catch (Exception e) {
            log.warn("拉取天天基金历史净值失败，fundCode：{}，{}", fundCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 同步单只基金基础信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FundBaseInfo syncFundBaseInfo(String fundCode) {
        log.info("开始同步基金基础信息：{}", fundCode);
        try {
            String fundName = null;
            String fundShortName = null;
            String fundType = null;
            String fundManager = null;
            String fundCompany = null;
            LocalDate establishDate = null;
            BigDecimal latestNetValue = null;
            BigDecimal latestChangeRate = null;

            // 1. 天天基金 - 净值 + 涨跌幅 + 名称
            try {
                String url = "https://fundgz.1234567.com.cn/js/" + fundCode + ".js";
                String result = Jsoup.connect(url).ignoreContentType(true).timeout(8000).execute().body();
                if (result.contains("jsonpgz(")) {
                    String jsonStr = result.substring(result.indexOf("jsonpgz(") + 8, result.lastIndexOf(");"));
                    JSONObject json = JSON.parseObject(jsonStr);
                    fundName = json.getString("name");
                    fundShortName = json.getString("name");
                    String dwjz = json.getString("dwjz");
                    String gszzl = json.getString("gszzl");
                    if (dwjz != null && !dwjz.isEmpty()) latestNetValue = new BigDecimal(dwjz);
                    if (gszzl != null && !gszzl.isEmpty()) latestChangeRate = new BigDecimal(gszzl);
                    log.info("天天基金同步成功：{}，净值{}，涨跌{}%", fundName, latestNetValue, latestChangeRate);
                }
            } catch (Exception e) {
                log.warn("天天基金接口失败：{}", e.getMessage());
            }

            // 2. 腾讯财经 - 兜底名称+净值
            if (fundName == null) {
                try {
                    String url = "https://qt.gtimg.cn/q=jj" + fundCode;
                    String result = Jsoup.connect(url).ignoreContentType(true).timeout(8000).execute().body();
                    if (result.contains("~")) {
                        String[] fields = result.replaceAll(".*\"", "").split("~");
                        if (fields.length > 1) fundName = fields[1];
                        if (fields.length > 5 && !fields[5].isEmpty()) {
                            latestNetValue = new BigDecimal(fields[5]);
                            latestChangeRate = BigDecimal.ZERO;
                        }
                        log.info("腾讯财经同步成功：{}，净值{}", fundName, latestNetValue);
                    }
                } catch (Exception e) {
                    log.warn("腾讯财经接口失败：{}", e.getMessage());
                }
            }

            // 3. 东方财富 - 基金元数据（类型、风险等级、经理、公司、成立日期）
            // API: fundmobapi.eastmoney.com/FundMApi/FundDetailInformation.ashx
            // 返回字段：FTYPE=类型, JJJL=基金经理, JJGS=基金公司, ESTABDATE=成立日期, RISKLEVEL=风险等级
            Byte riskLevelByte = null;
            try {
                String detailUrl = "https://fundmobapi.eastmoney.com/FundMApi/FundDetailInformation.ashx"
                        + "?FCODE=" + fundCode + "&deviceid=wap&plat=Wap&product=EFund&version=2.0.0";
                String detailResult = Jsoup.connect(detailUrl)
                        .header("Referer", "https://m.1234567.com.cn/")
                        .ignoreContentType(true).timeout(8000).execute().body();
                JSONObject detailJson = JSON.parseObject(detailResult);
                if (detailJson != null && detailJson.getInteger("ErrCode") != null && detailJson.getInteger("ErrCode") == 0) {
                    JSONObject data = detailJson.getJSONObject("Datas");
                    if (data != null) {
                        if (fundName == null) fundName = data.getString("FULLNAME");
                        if (fundShortName == null) fundShortName = data.getString("SHORTNAME");
                        fundType = data.getString("FTYPE");
                        String riskStr = data.getString("RISKLEVEL");
                        if (riskStr != null) {
                            try {
                                riskLevelByte = Byte.parseByte(riskStr.replace("R", ""));
                            } catch (NumberFormatException ignored) {}
                        }
                        // JJJL 是基金经理字符串，逗号分隔
                        fundManager = data.getString("JJJL");
                        fundCompany = data.getString("JJGS");
                        String foundDate = data.getString("ESTABDATE");
                        if (foundDate != null && !foundDate.isEmpty()) {
                            try {
                                establishDate = LocalDate.parse(foundDate);
                            } catch (Exception ignored) {}
                        }
                        log.info("东方财富基金详情同步成功：{}，类型{}，风险{}，经理{}，公司{}，成立{}",
                                fundShortName, fundType, riskLevelByte, fundManager, fundCompany, establishDate);
                    }
                }
            } catch (Exception e) {
                log.warn("东方财富基金详情接口失败：{}", e.getMessage());
            }

            if (!StringUtils.hasText(fundName)) fundName = "基金" + fundCode;
            if (!StringUtils.hasText(fundShortName)) fundShortName = fundName;

            FundBaseInfo fund = FundBaseInfo.builder()
                    .fundCode(fundCode)
                    .fundName(fundName)
                    .fundShortName(fundShortName)
                    .latestNetValue(latestNetValue)
                    .latestChangeRate(latestChangeRate)
                    .build();
            fund.setFundType(fundType);
            fund.setRiskLevel(riskLevelByte);
            fund.setFundManager(fundManager);
            fund.setFundCompany(fundCompany);
            fund.setEstablishDate(establishDate);
            fund.setCreateTime(LocalDateTime.now());
            fund.setUpdateTime(LocalDateTime.now());

            LambdaQueryWrapper<FundBaseInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(FundBaseInfo::getFundCode, fundCode);
            FundBaseInfo existFund = this.getOne(wrapper);
            if (existFund != null) {
                fund.setId(existFund.getId());
                // 合并：新数据覆盖已有的，空的不覆盖
                if (!StringUtils.hasText(fund.getFundType())) fund.setFundType(existFund.getFundType());
                if (fund.getRiskLevel() == null) fund.setRiskLevel(existFund.getRiskLevel());
                if (!StringUtils.hasText(fund.getFundManager())) fund.setFundManager(existFund.getFundManager());
                if (!StringUtils.hasText(fund.getFundCompany())) fund.setFundCompany(existFund.getFundCompany());
                if (fund.getEstablishDate() == null) fund.setEstablishDate(existFund.getEstablishDate());
                if (fund.getLatestNetValue() == null) fund.setLatestNetValue(existFund.getLatestNetValue());
                if (fund.getLatestChangeRate() == null) fund.setLatestChangeRate(existFund.getLatestChangeRate());
                this.updateById(fund);
                log.info("基金基础信息更新成功：{}", fundCode);
            } else {
                this.save(fund);
                log.info("基金基础信息新增成功：{}", fundCode);
            }
            // 清除 Redis 缓存，下次查询重新加载
            try {
                redisTemplate.delete(List.of("fund:info:" + fundCode, "fund:all:list"));
            } catch (Exception ignored) {}
            return fund;
        } catch (Exception e) {
            log.error("同步基金基础信息失败，基金代码：{}，错误：", fundCode, e);
            throw new BusinessException("同步基金基础信息失败：" + e.getMessage());
        }
    }

    /**
     * 批量同步基金基础信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSyncFundBaseInfo(List<String> fundCodeList) {
        int success = 0, fail = 0;
        for (String fundCode : fundCodeList) {
            try {
                syncFundBaseInfo(fundCode);
                success++;
            } catch (Exception e) {
                fail++;
                log.error("批量同步基金失败：{}", fundCode);
            }
        }
        log.info("批量同步基金基础信息完成：成功 {}，失败 {}", success, fail);
    }

    /**
     * 从东方财富同步单只基金最新官方净值（收盘后定时任务调用）
     * 只取最新一条，如果净值日期是今天则写入 fund_net_value_history 并更新 fund_base_info
     */
    @Override
    public boolean syncLatestOfficialNav(String fundCode) {
        try {
            String url = String.format("https://api.fund.eastmoney.com/f10/lsjz?fundCode=%s&pageIndex=1&pageSize=1", fundCode);
            String result = cn.hutool.http.HttpUtil.createGet(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "http://fund.eastmoney.com/")
                    .timeout(5000)
                    .execute()
                    .body();
            JSONObject json = JSON.parseObject(result);
            JSONObject data = json.getJSONObject("Data");
            if (data == null) return false;
            com.alibaba.fastjson.JSONArray lsjzList = data.getJSONArray("LSJZList");
            if (lsjzList == null || lsjzList.isEmpty()) return false;

            JSONObject item = lsjzList.getJSONObject(0);
            String dateStr = item.getString("FSRQ");
            String unitNav = item.getString("DWJZ");
            if (dateStr == null || unitNav == null || !StringUtils.hasText(unitNav)) return false;

            LocalDate navDate = LocalDate.parse(dateStr);

            // 检查是否已存在（不再限制必须是今天，避免应用未运行时丢失历史净值）
            FundNetValueHistory exist = fundNetValueHistoryMapper.selectOne(
                    new LambdaQueryWrapper<FundNetValueHistory>()
                            .eq(FundNetValueHistory::getFundCode, fundCode)
                            .eq(FundNetValueHistory::getNetValueDate, navDate));
            if (exist == null) {
                String cumulativeNav = item.getString("LJJZ");
                String changeRate = item.getString("JZZZL");
                FundNetValueHistory record = FundNetValueHistory.builder()
                        .fundCode(fundCode).netValueDate(navDate)
                        .unitNetValue(new BigDecimal(unitNav))
                        .cumulativeNetValue(StringUtils.hasText(cumulativeNav) ? new BigDecimal(cumulativeNav) : null)
                        .dailyChangeRate(StringUtils.hasText(changeRate) ? new BigDecimal(changeRate) : null)
                        .createTime(LocalDateTime.now()).build();
                fundNetValueHistoryMapper.insert(record);
                log.info("基金 {} 今日官方净值 {} 写入 fund_net_value_history", fundCode, unitNav);

                // 同时写入 Redis ZSet 历史净值缓存
                FundNetValueVO netValueVO = new FundNetValueVO();
                netValueVO.setNetValueDate(navDate);
                netValueVO.setUnitNetValue(new BigDecimal(unitNav));
                netValueVO.setCumulativeNetValue(StringUtils.hasText(cumulativeNav) ? new BigDecimal(cumulativeNav) : null);
                netValueVO.setDailyChangeRate(StringUtils.hasText(changeRate) ? new BigDecimal(changeRate) : null);
                String member = JSON.toJSONString(netValueVO);
                double score = navDate.toEpochDay();
                redisTemplate.opsForZSet().add("net_value:" + fundCode, member, score);
                log.info("基金 {} 官方净值已写入 Redis 历史净值 ZSet", fundCode);
            }

            // 同步更新 fund_base_info 的最新净值和涨跌幅
            FundBaseInfo fund = this.getOne(new LambdaQueryWrapper<FundBaseInfo>()
                    .eq(FundBaseInfo::getFundCode, fundCode));
            String changeRate = item.getString("JZZZL");
            if (fund != null) {
                fund.setLatestNetValue(new BigDecimal(unitNav));
                if (StringUtils.hasText(changeRate)) {
                    fund.setLatestChangeRate(new BigDecimal(changeRate));
                }
                fund.setUpdateTime(LocalDateTime.now());
                this.updateById(fund);
                log.info("基金 {} fund_base_info 更新：净值 {}，涨跌 {}%", fundCode, unitNav, changeRate);
            }

            // 用官方净值覆盖 Redis 估值缓存
            FundRealtimeValuationVO officialVO = new FundRealtimeValuationVO();
            officialVO.setFundCode(fundCode);
            officialVO.setFundName(fund != null ? fund.getFundName() : null);
            officialVO.setEstimateNetValue(new BigDecimal(unitNav));
            officialVO.setEstimateChangeRate(StringUtils.hasText(changeRate) ? new BigDecimal(changeRate) : BigDecimal.ZERO);
            officialVO.setValuationTime(LocalDateTime.now());
            officialVO.setValuationStatusDesc("官方净值（已公布）");
            String voJson = JSON.toJSONString(officialVO);
            redisTemplate.opsForValue().set("valuation:latest:" + fundCode, voJson, 14, TimeUnit.DAYS);
            log.info("基金 {} 官方净值已写入 Redis 估值缓存", fundCode);

            return true;
        } catch (Exception e) {
            log.warn("同步基金 {} 官方净值失败：{}", fundCode, e.getMessage());
            return false;
        }
    }
}
