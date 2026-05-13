package com.fund.assistant.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.entity.*;
import com.fund.assistant.exception.BusinessException;
import com.fund.assistant.mapper.*;
import com.fund.assistant.entity.FundNetValueHistory;
import com.fund.assistant.mapper.FundNetValueHistoryMapper;
import com.fund.assistant.service.FundRealtimeValuationService;
import com.fund.assistant.service.FundStockHoldDetailService;
import com.fund.assistant.util.MarketTimeUtils;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.FundRealtimeValuationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FundRealtimeValuationServiceImpl extends ServiceImpl<FundRealtimeValuationMapper, FundRealtimeValuation> implements FundRealtimeValuationService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private FundBaseInfoMapper fundBaseInfoMapper;

    @Autowired
    private FundStockHoldDetailMapper fundStockHoldDetailMapper;

    @Autowired
    private StockRealtimeQuoteMapper stockRealtimeQuoteMapper;

    @Autowired
    private UserFundFavoriteMapper userFundFavoriteMapper;

    @Autowired
    private UserFundHoldMapper userFundHoldMapper;

    @Autowired
    private FundStockHoldDetailService fundStockHoldDetailService;

    @Autowired
   private FundRealtimeValuationMapper fundRealtimeValuationMapper;

    @Autowired
    private FundNetValueHistoryMapper fundNetValueHistoryMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 精度配置
    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final BigDecimal DEFAULT_STOCK_POSITION = new BigDecimal("80.00");
    private static final byte STATUS_NORMAL = 1;
    private static final byte STATUS_NO_DATA = 2;
    // 股票行情按需缓存（60秒过期）
    private final java.util.Map<String, StockCacheEntry> stockCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 计算单只基金的实时估值
     */
    @Override
    public FundRealtimeValuationVO calculateFundRealtimeValuation(String fundCode) {
        return doCalculateValuation(fundCode, false);
    }

    /**
     * 核心估值计算（forceRefresh=true 跳过 Redis 缓存，供批量定时任务使用）
     */
    private FundRealtimeValuationVO doCalculateValuation(String fundCode, boolean forceRefresh) {
        // 0. 非交易时段一律不调外部接口，直接返回 Redis 缓存（含 forceRefresh 场景）
        boolean inTrading = MarketTimeUtils.isAStockTradingTime();
        if (!inTrading) {
            FundRealtimeValuationVO cached = getCachedLatestFromRedis(fundCode);
            if (cached != null) {
                log.info("基金 {} 非交易时段，直接返回 Redis 缓存数据", fundCode);
                return cached;
            }
            log.info("基金 {} 非交易时段且无缓存，不调外部接口", fundCode);
            return null;
        }
        // 交易时段内，非强制刷新时检查60秒缓存
        if (!forceRefresh) {
            FundRealtimeValuationVO cached = getCachedLatestFromRedis(fundCode);
            if (cached != null && cached.getValuationTime() != null) {
                long secondsAgo = java.time.Duration.between(cached.getValuationTime(), LocalDateTime.now()).getSeconds();
                if (secondsAgo < 60) {
                    log.info("基金 {} 估值缓存有效（{}秒前），直接返回 Redis 数据", fundCode, secondsAgo);
                    return cached;
                }
            }
        }

        // 1. 优先：天天基金接口
        FundRealtimeValuationVO ttfValuation = getValuationFromTiantian(fundCode);
        if (ttfValuation != null && ttfValuation.getValuationTime() != null
                && ttfValuation.getValuationTime().toLocalDate().equals(LocalDate.now())) {
            log.info("基金 {} 估值来源：天天基金", fundCode);
            cacheLatest(ttfValuation);
            appendToTimeline(ttfValuation);
            return ttfValuation;
        }
        if (ttfValuation != null) {
            log.warn("基金 {} 天天基金估值时间非今日（{}），视为无效，降级自研", fundCode, ttfValuation.getValuationTime());
        }

        // 2. 降级：重仓股加权算法
        log.warn("基金 {} 天天基金接口不可用或数据过期，自动降级为自研估值算法", fundCode);
        FundRealtimeValuationVO selfValuation = calculateValuationBySelfAlgorithm(fundCode);
        if (selfValuation != null) {
            log.info("基金 {} 估值来源：自研算法", fundCode);
            cacheLatest(selfValuation);
            appendToTimeline(selfValuation);
            return selfValuation;
        }

        throw new BusinessException("基金估值失败：所有数据源均不可用");
    }

    private FundRealtimeValuationVO getCachedLatestFromRedis(String fundCode) {
        try {
            String json = stringRedisTemplate.opsForValue().get("valuation:latest:" + fundCode);
            if (json != null) return JSON.parseObject(json, FundRealtimeValuationVO.class);
        } catch (Exception e) {
            log.warn("读取估值缓存失败，fundCode：{}", fundCode);
        }
        return null;
    }

    //天天基金接口：GBK编码，
    private FundRealtimeValuationVO getValuationFromTiantian(String fundCode) {
        try {
            String url = "https://fundgz.1234567.com.cn/js/" + fundCode + ".js";
            log.info("调用天天基金估值接口：{}", url);

            // 用 Jsoup 请求，绕过 Content-Type 问题
            org.jsoup.Connection.Response response = org.jsoup.Jsoup.connect(url)
                    .ignoreContentType(true)
                    .execute();

            String result = response.body();
            log.info("天天基金接口返回内容：{}", result);

            if (!result.contains("jsonpgz(")) {
                log.warn("天天基金返回格式异常，无jsonpgz包裹");
                return null;
            }

            // 解析 JSON
            String jsonStr = result.substring(result.indexOf("jsonpgz(") + 8, result.lastIndexOf(");"));
            JSONObject json = JSON.parseObject(jsonStr);

            FundRealtimeValuationVO vo = new FundRealtimeValuationVO();
            vo.setFundCode(json.getString("fundcode"));
            vo.setFundName(json.getString("name"));
            vo.setPreCloseNetValue(json.getBigDecimal("dwjz"));
            vo.setEstimateNetValue(json.getBigDecimal("gsz"));
            vo.setEstimateChangeRate(json.getBigDecimal("gszzl"));

            String timeStr = json.getString("gztime");
            if (StringUtils.hasText(timeStr)) {
                vo.setValuationTime(LocalDateTime.parse(timeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            }
            vo.setValuationStatusDesc("估值正常(天天基金)");

            // 补充基金信息
            fillFundInfo(vo);
            return vo;

        } catch (Exception e) {
            log.error("天天基金接口异常，基金代码：{}，错误：", fundCode, e);
            return null;
        }
    }

    //2. 估值算法
    private FundRealtimeValuationVO calculateValuationBySelfAlgorithm(String fundCode) {
        // 1. 基金基础信息
        FundBaseInfo fund = fundBaseInfoMapper.selectOne(
                new LambdaQueryWrapper<FundBaseInfo>().eq(FundBaseInfo::getFundCode, fundCode)
        );
        if (fund == null) throw new BusinessException("基金不存在");

        // 最新净值从历史净值表取（比fund_base_info更准确）
        BigDecimal preNet = fund.getLatestNetValue();
        FundNetValueHistory latestNav = fundNetValueHistoryMapper.selectOne(
                new LambdaQueryWrapper<FundNetValueHistory>()
                        .eq(FundNetValueHistory::getFundCode, fundCode)
                        .orderByDesc(FundNetValueHistory::getNetValueDate)
                        .last("LIMIT 1"));
        if (latestNav != null && latestNav.getUnitNetValue() != null) {
            preNet = latestNav.getUnitNetValue();
        }
        if (preNet == null || preNet.compareTo(BigDecimal.ZERO) <= 0) return null;

        // 2. 获取重仓股（替换为懒加载方法：自动爬取+更新）
        List<FundStockHoldDetail> holdList = getOrUpdateFundHoldDetail(fundCode);
        if (holdList.isEmpty()) {
            throw new BusinessException("暂无重仓股数据，无法自研估值");
        }

        // 3. 按需拉取股票实时行情（不再走定时任务+DB）
        List<String> stockCodes = holdList.stream().map(FundStockHoldDetail::getStockCode).collect(Collectors.toList());
        java.util.Map<String, BigDecimal> quoteMap = fetchStockQuotes(stockCodes);

        // 4. 核心加权估值算法
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalChange = BigDecimal.ZERO;

        for (FundStockHoldDetail hold : holdList) {
            BigDecimal cr = quoteMap.get(hold.getStockCode());
            if (cr == null) continue;

            BigDecimal ratio = hold.getHoldRatio().divide(new BigDecimal("100"), SCALE, ROUNDING_MODE);
            BigDecimal change = cr.divide(new BigDecimal("100"), SCALE, ROUNDING_MODE);

            totalChange = totalChange.add(ratio.multiply(change));
            totalWeight = totalWeight.add(ratio);
        }

        // 归一化
        if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            totalChange = totalChange.divide(totalWeight, SCALE, ROUNDING_MODE);
        }

        // 仓位影响
        BigDecimal position = fund.getStockPositionRatio() == null ? DEFAULT_STOCK_POSITION : fund.getStockPositionRatio();
        BigDecimal fundChange = totalChange.multiply(position.divide(new BigDecimal("100"), SCALE, ROUNDING_MODE));
        BigDecimal estimateNet = preNet.multiply(BigDecimal.ONE.add(fundChange)).setScale(SCALE, ROUNDING_MODE);
        BigDecimal estimateChangePercent = fundChange.multiply(new BigDecimal("100")).setScale(2, ROUNDING_MODE);
        log.info("自研加权计算：{} 只股票参与，总权重 {}%，加权涨跌 {}%，仓位系数 {}%，最终估算涨幅 {}%",
                quoteMap.size(), totalWeight.multiply(new BigDecimal("100")).setScale(1, ROUNDING_MODE),
                totalChange.multiply(new BigDecimal("100")).setScale(2, ROUNDING_MODE),
                position, estimateChangePercent);

        // 5. 封装 VO
        FundRealtimeValuationVO vo = new FundRealtimeValuationVO();
        vo.setFundCode(fundCode);
        vo.setFundName(fund.getFundName());
        vo.setFundShortName(fund.getFundShortName());
        vo.setPreCloseNetValue(preNet);
        vo.setEstimateNetValue(estimateNet);
        vo.setEstimateChangeRate(estimateChangePercent);
        vo.setValuationTime(LocalDateTime.now());
        // 检测是否含海外持仓（港股5位数字、美股含字母）
        boolean hasOverseas = holdList.stream().anyMatch(h -> {
            String code = h.getStockCode();
            return code.matches("\\d{5}") || !code.matches("\\d+");
        });
        vo.setValuationStatusDesc(hasOverseas ? "估值正常(自研算法，海外持仓仅供参考)" : "估值正常(自研算法)");
        vo.setLatestNetValue(fund.getLatestNetValue());
        vo.setLatestChangeRate(fund.getLatestChangeRate());
        vo.setStockPositionRatio(position);

        return vo;
    }

    /**
     * 查询单只基金最新估值
     */
    // 3. 查询接口实现
    @Override
    public FundRealtimeValuationVO getLatestValuationByFundCode(String fundCode) {
        // 优先查 Redis
        FundRealtimeValuationVO cached = getCachedLatestFromRedis(fundCode);
        if (cached != null) {
            log.debug("估值缓存命中：{}", fundCode);
            return cached;
        }
        // 实时计算
        FundRealtimeValuationVO realtime = doCalculateValuation(fundCode, false);
        if (realtime != null) return realtime;
        // 实时估值不可用（非交易时段），用官方净值兜底
        return buildOfficialNavFallback(fundCode);
    }

    /** 非交易时段用官方净值构建估值VO */
    private FundRealtimeValuationVO buildOfficialNavFallback(String fundCode) {
        FundBaseInfo fund = fundBaseInfoMapper.selectOne(
                new LambdaQueryWrapper<FundBaseInfo>().eq(FundBaseInfo::getFundCode, fundCode));
        if (fund == null || fund.getLatestNetValue() == null) return null;

        // 查最近两条净值，最新=当前净值，次新=前收净值
        List<FundNetValueHistory> recentNavs = fundNetValueHistoryMapper.selectList(
                new LambdaQueryWrapper<FundNetValueHistory>()
                        .eq(FundNetValueHistory::getFundCode, fundCode)
                        .orderByDesc(FundNetValueHistory::getNetValueDate)
                        .last("LIMIT 2"));
        BigDecimal nav = recentNavs.size() > 0 ? recentNavs.get(0).getUnitNetValue() : fund.getLatestNetValue();
        BigDecimal preClose = recentNavs.size() > 1 ? recentNavs.get(1).getUnitNetValue() : nav;
        BigDecimal changeRate = recentNavs.size() > 0 && recentNavs.get(0).getDailyChangeRate() != null
                ? recentNavs.get(0).getDailyChangeRate() : BigDecimal.ZERO;

        FundRealtimeValuationVO vo = new FundRealtimeValuationVO();
        vo.setFundCode(fundCode);
        vo.setFundName(fund.getFundName());
        vo.setFundShortName(fund.getFundShortName());
        vo.setEstimateNetValue(nav);
        vo.setPreCloseNetValue(preClose);
        vo.setEstimateChangeRate(changeRate);
        vo.setValuationTime(LocalDateTime.now());
        vo.setValuationStatusDesc("官方净值（非交易时段）");
        vo.setLatestNetValue(nav);
        vo.setLatestChangeRate(changeRate);
        log.debug("基金 {} 使用官方净值兜底：{}，前收 {}，涨跌 {}%", fundCode, nav, preClose, changeRate);
        return vo;
    }

    @Override
    // 批量计算所有符合类型基金的实时估值
    public void batchCalculateAllFundValuation() {
        // 全部基金都尝试估值（天天基金覆盖绝大多数类型，自研算法做降级兜底）
        List<FundBaseInfo> fundList = fundBaseInfoMapper.selectList(null);
        // 2. 定义计数器：记录批量估值成功多少、失败多少
        int success = 0, fail = 0;
        // 3. 循环遍历每一只筛选出来的基金
        for (FundBaseInfo fund : fundList) {
            try {
                // 4. 给当前这只基金 执行一遍完整估值计算
                doCalculateValuation(fund.getFundCode(), true);
                // 执行没报错  成功数+1
                success++;
            } catch (Exception e) {
                // 中间出异常  失败数+1
                fail++;
                // 打印日志：哪只基金批量估值失败、原因是什么
                log.error("基金 {} 批量估值失败: {}", fund.getFundCode(), e.getMessage());
            }
        }
        // 5. 全部跑完，打印汇总日志：一共成功多少、失败多少
        log.info("批量估值完成：成功 {}，失败 {}", success, fail);
    }

    /**
     * 查询用户自选基金最新估值列表
     */
    @Override
    public List<FundRealtimeValuationVO> getFavoriteFundValuationList() {
        Long userId = UserContext.getUserId();
        // 2. 去数据库查：这个用户自选了哪些基金
        List<UserFundFavorite> favorites = userFundFavoriteMapper.selectList(
                new LambdaQueryWrapper<UserFundFavorite>()
                        .eq(UserFundFavorite::getUserId, userId)
        );

        // 3.  直接返回空列表
        if (favorites.isEmpty()) return List.of();
        // 4. 遍历用户的自选基金
        return favorites.stream()
                .map(f -> {
                    try {
                        //调用方法查这只基金的最新估值
                        return getLatestValuationByFundCode(f.getFundCode());
                    } catch (Exception e) {
                        // 某只基金报错了 跳过它，不影响整个列表
                        return null;
                    }
                })
                // 过滤掉报错、null的数据
                .filter(vo -> vo != null)
                // 最终返回估值VO列表给前端
                .collect(Collectors.toList());
    }

    /**
     * 查询用户持仓基金最新估值列表
     */
    @Override
    public List<FundRealtimeValuationVO> getHoldFundValuationList() {
        Long userId = UserContext.getUserId();
        List<UserFundHold> holds = userFundHoldMapper.selectList(
                new LambdaQueryWrapper<UserFundHold>()
                        .eq(UserFundHold::getUserId, userId)
                        .gt(UserFundHold::getHoldShares, BigDecimal.ZERO)
        );
        if (holds.isEmpty()) return List.of();

        return holds.stream()
                .map(h -> {
                    try {
                        return getLatestValuationByFundCode(h.getFundCode());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(vo -> vo != null)
                .collect(Collectors.toList());
    }

    // 重仓股自动爬取与更新（懒加载+3天时效）
    /**
     * 获取基金重仓股（懒加载：用户看的时候才检查更新）
     * 1. 先查数据库
     * 2. 如果没有数据，或者最新数据超过3天，自动爬取天天基金
     * 3. 爬取成功后：删除旧数据 → 插入新数据（原子操作）
     */
    @Transactional(rollbackFor = Exception.class) // 必须加事务，保证删除+插入的原子性
    public List<FundStockHoldDetail> getOrUpdateFundHoldDetail(String fundCode) {
        // 1. 先查数据库最新的重仓股数据
        LambdaQueryWrapper<FundStockHoldDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundStockHoldDetail::getFundCode, fundCode);
        wrapper.orderByDesc(FundStockHoldDetail::getReportDate);
        wrapper.last("LIMIT 1");
        FundStockHoldDetail latestHold = fundStockHoldDetailMapper.selectOne(wrapper);

        // 2. 判断是否需要更新
        boolean needUpdate = false;
        if (latestHold == null) {
            // 数据库里没有，必须爬取
            needUpdate = true;
        } else {
            // 有数据，检查是否超过3天
            LocalDate latestDate = latestHold.getReportDate() != null ? latestHold.getReportDate() : LocalDate.MIN;
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(latestDate, LocalDate.now());
            if (daysBetween >= 3) {
                needUpdate = true;
            }
        }

        // 3. 需要更新，执行爬取
        if (needUpdate) {
            log.info("基金 {} 重仓股数据需要更新，开始爬取天天基金", fundCode);
            List<FundStockHoldDetail> newHoldList = crawlFundHoldFromEastMoney(fundCode);
            if (newHoldList != null && !newHoldList.isEmpty()) {
                // 先删除旧数据
                LambdaQueryWrapper<FundStockHoldDetail> deleteWrapper = new LambdaQueryWrapper<>();
                deleteWrapper.eq(FundStockHoldDetail::getFundCode, fundCode);
                fundStockHoldDetailMapper.delete(deleteWrapper);

                // 用重仓股Service批量插入，类型匹配
                fundStockHoldDetailService.saveBatch(newHoldList);
                log.info("基金 {} 重仓股数据更新成功，共 {} 只", fundCode, newHoldList.size());
                return newHoldList;
            }
        }

        // 4. 不需要更新，或者更新失败，返回数据库现有数据
        return fundStockHoldDetailMapper.selectList(
                new LambdaQueryWrapper<FundStockHoldDetail>()
                        .eq(FundStockHoldDetail::getFundCode, fundCode)
                        .orderByDesc(FundStockHoldDetail::getHoldRatio)
        );
    }

    /**
     * 爬取天天基金重仓股数据
     */
    private List<FundStockHoldDetail> crawlFundHoldFromEastMoney(String fundCode) {
        try {
            String url = "https://fundf10.eastmoney.com/FundArchivesDatas.aspx?type=jjcc&code=" + fundCode + "&topline=10";
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(url)
                    .header("Referer", "https://fundf10.eastmoney.com/")
                    .ignoreContentType(true)
                    .timeout(8000)
                    .get();

            org.jsoup.select.Elements tables = doc.select("table");
            org.jsoup.nodes.Element targetTable = null;
            for (org.jsoup.nodes.Element t : tables) {
                if (t.select("tr").size() >= 3) { targetTable = t; break; }
            }
            if (targetTable == null) return null;

            org.jsoup.select.Elements rows = targetTable.select("tr");
            List<FundStockHoldDetail> holdList = new java.util.ArrayList<>();
            for (int i = 1; i < rows.size(); i++) {
                org.jsoup.select.Elements tds = rows.get(i).select("td");
                if (tds.size() < 7) continue;
                try {
                    String stockCode = tds.get(1).text().trim();
                    String stockName = tds.get(2).text().trim();
                    String holdRatioStr = tds.get(6).text().replace("%", "").trim();
                    if (stockCode.isEmpty() || stockName.isEmpty() || holdRatioStr.isEmpty()) continue;

                    FundStockHoldDetail hold = FundStockHoldDetail.builder()
                            .fundCode(fundCode).stockCode(stockCode).stockName(stockName)
                            .holdRatio(new BigDecimal(holdRatioStr))
                            .reportDate(LocalDate.now()).createTime(LocalDateTime.now()).updateTime(LocalDateTime.now())
                            .build();
                    holdList.add(hold);
                } catch (Exception e) {
                    log.warn("单条重仓股解析失败：{}", e.getMessage());
                }
            }
            return holdList;
        } catch (Exception e) {
            log.error("爬取天天基金重仓股失败，基金代码：{}，错误：{}", fundCode, e.getMessage());
            return null;
        }
    }

    //  工具方法
    // 给基金估值VO 补充 基金基础信息
    private void fillFundInfo(FundRealtimeValuationVO vo) {
        FundBaseInfo fund = fundBaseInfoMapper.selectOne(
                new LambdaQueryWrapper<FundBaseInfo>().eq(FundBaseInfo::getFundCode, vo.getFundCode())
        );
        if (fund != null) {
            if (!StringUtils.hasText(vo.getFundName())) {
                vo.setFundName(fund.getFundName());
            }
            vo.setFundShortName(fund.getFundShortName());
            vo.setStockPositionRatio(fund.getStockPositionRatio());
            // 最新净值从历史净值表取（比fund_base_info更准确）
            FundNetValueHistory latestNav = fundNetValueHistoryMapper.selectOne(
                    new LambdaQueryWrapper<FundNetValueHistory>()
                            .eq(FundNetValueHistory::getFundCode, vo.getFundCode())
                            .orderByDesc(FundNetValueHistory::getNetValueDate)
                            .last("LIMIT 1"));
            if (latestNav != null) {
                vo.setLatestNetValue(latestNav.getUnitNetValue());
                vo.setLatestChangeRate(latestNav.getDailyChangeRate());
            } else {
                vo.setLatestNetValue(fund.getLatestNetValue());
                vo.setLatestChangeRate(fund.getLatestChangeRate());
            }
        }
    }

    /**
     * 保存估值记录
     */
    private void saveValuation(FundRealtimeValuationVO vo, Byte status) {
        try {
            // 1. 构建实体对象，拷贝VO同名属性
            FundRealtimeValuation valuation = new FundRealtimeValuation();
            org.springframework.beans.BeanUtils.copyProperties(vo, valuation);

            // 2. 补充必填字段
            valuation.setValuationTime(vo.getValuationTime() == null ? LocalDateTime.now() : vo.getValuationTime());
            valuation.setValuationStatus(status);
            valuation.setUpdateTime(LocalDateTime.now());

            // 3. 查询是否已存在同基金同时段的估值
            LambdaQueryWrapper<FundRealtimeValuation> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(FundRealtimeValuation::getFundCode, vo.getFundCode());
            wrapper.eq(FundRealtimeValuation::getValuationTime, valuation.getValuationTime());
            FundRealtimeValuation exist = this.getOne(wrapper);

            // 4. 存在则更新，不存在则插入
            if (exist != null) {
                // 保留主键和原创建时间，执行更新
                valuation.setId(exist.getId());
                valuation.setCreateTime(exist.getCreateTime());
                this.updateById(valuation);
            } else {
                // 新增设置创建时间
                valuation.setCreateTime(LocalDateTime.now());
                this.save(valuation);
            }

        } catch (Exception e) {
            log.error("保存估值记录失败，基金代码：{}，错误：", vo.getFundCode(), e);
        }
    }

    private String getStatusDesc(Byte status) {
        return status == 1 ? "估值正常" : "估值异常";
    }

    // ==================== Redis 缓存：盘中实时估值 ====================

    /**
     * 到当天 15:30 还有多少秒
     * 过期了返回 0，避免传负数给 Redis
     */
    private long secondsUntil1530() {
        LocalTime now = LocalTime.now();
        LocalTime closeTime = LocalTime.of(15, 30);
        if (now.isAfter(closeTime)) {
            return 0;
        }
        return Duration.between(now, closeTime).getSeconds();
    }

    /**
     * 到当天午夜还有多少秒
     */
    private long secondsUntilEndOfDay() {
        return Duration.between(LocalTime.now(), LocalTime.MAX).getSeconds() + 1;
    }

    /**
     * 把刚算好的估值存到 Redis，key 格式 valuation:latest:{fundCode}
     * TTL 到 15:30，收盘后这个最新估值就没用了
     */
    private void cacheLatest(FundRealtimeValuationVO vo) {
        try {
            String key = "valuation:latest:" + vo.getFundCode();
            String json = JSON.toJSONString(vo);
            long ttl = secondsUntil1530();
            if (ttl <= 0) ttl = secondsUntilEndOfDay(); // 保留到当晚，确保官方净值同步（最晚22:30）来覆盖前不过期
            stringRedisTemplate.opsForValue().set(key, json, ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入估值 latest 缓存失败，fundCode：{}，错误：{}", vo.getFundCode(), e.getMessage());
        }
    }

    /**
     * 把估值追加到 Redis List，key 格式 valuation:timeline:{fundCode}:{yyyyMMdd}
     * 用来画当天走势图，TTL 到午夜自动清掉
     */
    private void appendToTimeline(FundRealtimeValuationVO vo) {
        try {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String key = "valuation:timeline:" + vo.getFundCode() + ":" + dateStr;
            String json = JSON.toJSONString(vo);
            String newHM = vo.getValuationTime() != null
                    ? vo.getValuationTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null;
            // 同时间覆盖末条，不同时间追加
            Long size = stringRedisTemplate.opsForList().size(key);
            if (size != null && size > 0 && newHM != null) {
                String lastJson = stringRedisTemplate.opsForList().index(key, size - 1);
                FundRealtimeValuationVO last = lastJson != null
                        ? JSON.parseObject(lastJson, FundRealtimeValuationVO.class) : null;
                String lastHM = last != null && last.getValuationTime() != null
                        ? last.getValuationTime().format(DateTimeFormatter.ofPattern("HH:mm")) : null;
                if (newHM.equals(lastHM)) {
                    stringRedisTemplate.opsForList().set(key, size - 1, json);
                    return;
                }
            }
            stringRedisTemplate.opsForList().rightPush(key, json);
            long ttl = secondsUntilEndOfDay();
            if (ttl > 0) stringRedisTemplate.expire(key, ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("追加估值 timeline 失败，fundCode：{}，错误：{}", vo.getFundCode(), e.getMessage());
        }
    }

    // 股票行情按需拉取（替代定时任务），结果缓存60秒
    private static class StockCacheEntry { BigDecimal changeRate; long ts; StockCacheEntry(BigDecimal r, long t) { changeRate=r; ts=t; } }
    private java.util.Map<String, BigDecimal> fetchStockQuotes(List<String> codes) {
        java.util.Map<String, BigDecimal> result = new java.util.LinkedHashMap<>();
        long now = System.currentTimeMillis();
        java.util.List<String> needFetch = new ArrayList<>();
        for (String code : codes) {
            StockCacheEntry e = stockCache.get(code);
            if (e != null && (now - e.ts) < 60000) { result.put(code, e.changeRate); }
            else { needFetch.add(code); }
        }
        if (needFetch.isEmpty()) return result;
        try {
            java.util.List<String> fullCodes = needFetch.stream().map(c -> {
            if (c.startsWith("6")) return "sh" + c;
            if (c.startsWith("0") || c.startsWith("3")) return "sz" + c;
            if (c.length() <= 5 && !c.matches("\\d+")) return "us" + c.toUpperCase(); // 美股代码（含字母）
            if (c.matches("\\d{5}")) return "hk" + c; // 港股5位数字
            return "hk" + c;
        }).collect(Collectors.toList());
            String url = "https://qt.gtimg.cn/q=" + String.join(",", fullCodes);
            log.info("拉取股票实时行情，共 {} 只：{}", fullCodes.size(), url);
            String resp = restTemplate.getForObject(url, String.class);
            int got = 0;
            if (resp != null) for (String line : resp.split("\n")) {
                if (!line.contains("~")) continue;
                int qIdx = line.indexOf('"');
                if (qIdx < 0) continue;
                String content = line.substring(qIdx + 1);
                if (content.endsWith("\";")) content = content.substring(0, content.length() - 2);
                else if (content.endsWith("\"")) content = content.substring(0, content.length() - 1);
                String[] parts = content.split("~");
                if (parts.length < 30) continue; // 美股格式字段较少
                // 股票代码在 parts[2]（A股直接是代码，美股带.N/.OQ后缀需去掉）
                String rawCode = parts[2].trim();
                int dotIdx = rawCode.indexOf('.');
                if (dotIdx > 0) rawCode = rawCode.substring(0, dotIdx);
                // 涨跌幅字段：A股/港股 index=31，美股 index=32
                BigDecimal cr;
                if (parts.length > 32 && parts[32] != null && !parts[32].isEmpty() && parts[32].matches("-?[\\d.]+")) {
                    cr = new BigDecimal(parts[32]);
                } else if (parts.length > 31 && parts[31] != null && !parts[31].isEmpty() && parts[31].matches("-?[\\d.]+")) {
                    cr = new BigDecimal(parts[31]);
                } else {
                    cr = BigDecimal.ZERO;
                }
                result.put(rawCode, cr);
                stockCache.put(rawCode, new StockCacheEntry(cr, now));
                got++;
            }
            log.info("股票行情拉取完成：请求 {} 只，获取 {} 只", needFetch.size(), got);
        } catch (Exception e) { log.warn("按需拉取股票行情失败：{}", e.getMessage()); }
        // 拿不到行情的股票不参与计算，不再默认塞0%
        return result;
    }

    /**
     * 获取某只基金当天的全天走势数据（从 Redis List 读取）
     * 按时间正序返回，前端画折线图用
     */
    public List<FundRealtimeValuationVO> getTimeline(String fundCode) {
        try {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String key = "valuation:timeline:" + fundCode + ":" + dateStr;
            List<String> jsonList = stringRedisTemplate.opsForList().range(key, 0, -1);
            if (jsonList != null && !jsonList.isEmpty()) {
                return jsonList.stream()
                        .map(json -> JSON.parseObject(json, FundRealtimeValuationVO.class))
                        .collect(Collectors.toList());
            }
            FundRealtimeValuationVO latest = getLatestValuationByFundCode(fundCode);
            return latest != null ? List.of(latest) : List.of();
        } catch (Exception e) {
            log.warn("读取估值 timeline 失败，fundCode：{}，错误：{}", fundCode, e.getMessage());
            return List.of();
        }
    }
}