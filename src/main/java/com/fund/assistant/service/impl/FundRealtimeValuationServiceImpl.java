package com.fund.assistant.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.entity.*;
import com.fund.assistant.exception.BusinessException;
import com.fund.assistant.mapper.*;
import com.fund.assistant.service.FundRealtimeValuationService;
import com.fund.assistant.service.FundStockHoldDetailService;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.FundRealtimeValuationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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

    // 精度配置
    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    // 默认股票仓位 90%
    private static final BigDecimal DEFAULT_STOCK_POSITION = new BigDecimal("90.00");
    // 估值状态
    //估值状态（正常）
    private static final byte STATUS_NORMAL = 1;
    // 估值状态（没有数据）
    private static final byte STATUS_NO_DATA = 2;

    /**
     * 计算单只基金的实时估值
     */
    @Override
    public FundRealtimeValuationVO calculateFundRealtimeValuation(String fundCode) {
        // 1. 优先：天天基金接口（zhuyi :有 GBK 乱码）
        FundRealtimeValuationVO ttfValuation = getValuationFromTiantian(fundCode);
        if (ttfValuation != null) {
            log.info("基金 {} 估值来源：天天基金", fundCode);
            saveValuation(ttfValuation, STATUS_NORMAL);
            return ttfValuation;
        }

        // 2. 降级：重仓股加权算法
        log.warn("基金 {} 天天基金接口不可用，自动降级为自研估值算法", fundCode);
        FundRealtimeValuationVO selfValuation = calculateValuationBySelfAlgorithm(fundCode);
        if (selfValuation != null) {
            log.info("基金 {} 估值来源：自研算法", fundCode);
            saveValuation(selfValuation, STATUS_NORMAL);
            return selfValuation;
        }

        throw new BusinessException("基金估值失败：所有数据源均不可用");
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

        //获得最新净值
        BigDecimal preNet = fund.getLatestNetValue();
        if (preNet == null || preNet.compareTo(BigDecimal.ZERO) <= 0) return null;

        // 2. 获取重仓股（✅ 替换为懒加载方法：自动爬取+更新）
        List<FundStockHoldDetail> holdList = getOrUpdateFundHoldDetail(fundCode);
        if (holdList.isEmpty()) {
            throw new BusinessException("暂无重仓股数据，无法自研估值");
        }

        // 3. 获取股票实时行情
        List<String> stockCodes = holdList.stream().map(FundStockHoldDetail::getStockCode).collect(Collectors.toList());
        List<StockRealtimeQuote> quoteList = stockRealtimeQuoteMapper.selectList(
                new LambdaQueryWrapper<StockRealtimeQuote>().in(StockRealtimeQuote::getStockCode, stockCodes)
        );
        Map<String, StockRealtimeQuote> quoteMap = quoteList.stream()
                .collect(Collectors.toMap(StockRealtimeQuote::getStockCode, q -> q));

        // 4. 核心加权估值算法
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalChange = BigDecimal.ZERO;

        for (FundStockHoldDetail hold : holdList) {
            StockRealtimeQuote quote = quoteMap.get(hold.getStockCode());
            if (quote == null) continue;

            BigDecimal ratio = hold.getHoldRatio().divide(new BigDecimal("100"), SCALE, ROUNDING_MODE);
            BigDecimal change = quote.getChangeRate().divide(new BigDecimal("100"), SCALE, ROUNDING_MODE);

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

        // 5. 封装 VO
        FundRealtimeValuationVO vo = new FundRealtimeValuationVO();
        vo.setFundCode(fundCode);
        vo.setFundName(fund.getFundName());
        vo.setFundShortName(fund.getFundShortName());
        vo.setPreCloseNetValue(preNet);
        vo.setEstimateNetValue(estimateNet);
        vo.setEstimateChangeRate(estimateChangePercent);
        vo.setValuationTime(LocalDateTime.now());
        vo.setValuationStatusDesc("估值正常(自研算法)");
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

        // 1. 先去数据库查：这个基金有没有已经算好的最新估值
        LambdaQueryWrapper<FundRealtimeValuation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FundRealtimeValuation::getFundCode, fundCode);
        wrapper.orderByDesc(FundRealtimeValuation::getValuationTime);
        wrapper.last("LIMIT 1");
        FundRealtimeValuation cached = this.getOne(wrapper);

        // 2. 如果数据库里有  直接返回，不用再算
        if (cached != null) {
            FundRealtimeValuationVO vo = new FundRealtimeValuationVO();
            BeanUtils.copyProperties(cached, vo);
            fillFundInfo(vo);
            vo.setValuationStatusDesc(getStatusDesc(cached.getValuationStatus()));
            return vo;
        }

        // 3. 如果数据库里没有  现场计算
        return calculateFundRealtimeValuation(fundCode);
    }

    @Override
    // 批量计算所有符合类型基金的实时估值
    public void batchCalculateAllFundValuation() {
        // 1. 从数据库查询：只拿 股票型、混合型、指数型 基金
        List<FundBaseInfo> fundList = fundBaseInfoMapper.selectList(
                new LambdaQueryWrapper<FundBaseInfo>()
                        .in(FundBaseInfo::getFundType, "股票型", "混合型", "指数型")
        );
        // 2. 定义计数器：记录批量估值成功多少、失败多少
        int success = 0, fail = 0;
        // 3. 循环遍历每一只筛选出来的基金
        for (FundBaseInfo fund : fundList) {
            try {
                // 4. 给当前这只基金 执行一遍完整估值计算
                calculateFundRealtimeValuation(fund.getFundCode());
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
            String url = "https://fund.eastmoney.com/f10/FundArchivesDatas.aspx?type=jjcc&code=" + fundCode + "&topline=10";
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(url).get();
            org.jsoup.select.Elements rows = doc.select("tbody tr");

            List<FundStockHoldDetail> holdList = new java.util.ArrayList<>();
            for (org.jsoup.nodes.Element row : rows) {
                org.jsoup.select.Elements tds = row.select("td");
                if (tds.size() < 5) continue;

                try {
                    // 提取股票代码
                    String stockCode = tds.get(1).select("a").attr("href").replace("http://quote.eastmoney.com/", "").replace(".html", "");
                    // 提取股票名称
                    String stockName = tds.get(1).select("a").text();
                    // 提取持仓占比
                    String holdRatioStr = tds.get(3).text().replace("%", "");

                    FundStockHoldDetail hold = FundStockHoldDetail.builder()
                            .fundCode(fundCode)
                            .stockCode(stockCode)
                            .stockName(stockName)
                            .holdRatio(new BigDecimal(holdRatioStr))
                            .reportDate(LocalDate.now())
                            .createTime(LocalDateTime.now())
                            .updateTime(LocalDateTime.now())
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

    /**
     * 简单的字符串提取工具
     */
    private String extractValue(String content, String startFlag, String endFlag) {
        if (!content.contains(startFlag) || !content.contains(endFlag)) {
            return null;
        }
        int start = content.indexOf(startFlag) + startFlag.length();
        int end = content.indexOf(endFlag, start);
        if (start < end) {
            return content.substring(start, end).trim();
        }
        return null;
    }

    //  工具方法
    // 给基金估值VO 补充 基金基础信息
    private void fillFundInfo(FundRealtimeValuationVO vo) {
        // 1. 根据基金代码，去数据库查基金完整信息
        FundBaseInfo fund = fundBaseInfoMapper.selectOne(
                new LambdaQueryWrapper<FundBaseInfo>().eq(FundBaseInfo::getFundCode, vo.getFundCode())
        );
        if (fund != null) {
            // 3. 如果VO里没有基金名称，就从数据库里设置进去
            if (!StringUtils.hasText(vo.getFundName())) {
                vo.setFundName(fund.getFundName());
            }
            // 4. 把基金的各种信息 塞进 VO
            vo.setFundShortName(fund.getFundShortName());       // 基金简称
            vo.setLatestNetValue(fund.getLatestNetValue());     // 最新净值（昨日净值）
            vo.setLatestChangeRate(fund.getLatestChangeRate()); // 昨日涨跌幅
            vo.setStockPositionRatio(fund.getStockPositionRatio()); // 股票仓位
        }
    }

    /**
     *
     * @param vo 估值数据 VO
     * @param status 状态码（1 正常 / 2 无数据 / 3 非交易时间）
     */
    private void saveValuation(FundRealtimeValuationVO vo, Byte status) {
        try {
            FundRealtimeValuation valuation = new FundRealtimeValuation();
            BeanUtils.copyProperties(vo, valuation);
            valuation.setValuationTime(vo.getValuationTime() == null ? LocalDateTime.now() : vo.getValuationTime());
            valuation.setValuationStatus(status);
            this.save(valuation);
        } catch (Exception e) {
            log.error("保存估值记录失败: {}", e.getMessage());
        }
    }

    private String getStatusDesc(Byte status) {
        return status == 1 ? "估值正常" : "估值异常";
    }
}