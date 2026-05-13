package com.fund.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.entity.FundBaseInfo;
import com.fund.assistant.entity.FundNetValueHistory;
import com.fund.assistant.entity.UserFundDailyProfit;
import com.fund.assistant.entity.UserFundHold;
import com.fund.assistant.exception.BusinessException;
import com.fund.assistant.mapper.FundBaseInfoMapper;
import com.fund.assistant.mapper.FundNetValueHistoryMapper;
import com.fund.assistant.mapper.UserFundDailyProfitMapper;
import com.fund.assistant.mapper.UserFundHoldMapper;
import com.fund.assistant.service.DailyProfitCacheService;
import com.fund.assistant.service.FundRealtimeValuationService;
import com.fund.assistant.service.UserFundDailyProfitService;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.FundRealtimeValuationVO;
import com.fund.assistant.vo.UserFundDailyProfitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 
 * 计算用户 单只/所有持仓基金 的今日实时收益
 * 流程 查持仓 查实时估值 计算收益 存入Redis 返回前端
 */
@Slf4j
@Service
public class UserFundDailyProfitServiceImpl extends ServiceImpl<UserFundDailyProfitMapper, UserFundDailyProfit>
        implements UserFundDailyProfitService {

    @Autowired
    private UserFundHoldMapper userFundHoldMapper;

    @Autowired
    private FundRealtimeValuationService fundRealtimeValuationService;

    @Autowired
    private DailyProfitCacheService dailyProfitCacheService;

    @Autowired
    private FundNetValueHistoryMapper fundNetValueHistoryMapper;

    @Autowired
    private FundBaseInfoMapper fundBaseInfoMapper;

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Override
    public UserFundDailyProfitVO getFundDailyProfit(String fundCode) {
        Long userId = UserContext.getUserId();

        UserFundDailyProfitVO cached = dailyProfitCacheService.getCachedFundProfit(userId, fundCode);
        if (cached != null) {
            return cached;
        }

        UserFundHold hold = userFundHoldMapper.selectOne(
                new LambdaQueryWrapper<UserFundHold>()
                        .eq(UserFundHold::getUserId, userId)
                        .eq(UserFundHold::getFundCode, fundCode)
                        .gt(UserFundHold::getHoldShares, BigDecimal.ZERO));
        if (hold == null) {
            throw new BusinessException("该基金不在您的持仓中");
        }
        // 调用第三方接口，获取基金实时估值
        FundRealtimeValuationVO valuation = fundRealtimeValuationService.getLatestValuationByFundCode(fundCode);
        UserFundDailyProfitVO vo = buildFundProfitVO(hold, valuation);
        // 持仓 估值 计算收益，封装成VO
        dailyProfitCacheService.cacheFundProfit(userId, fundCode, vo);

        return vo;
    }

    /**
     * 获取所有持仓基金 的今日实时收益
     * 
     * @return
     */
    @Override
    public List<UserFundDailyProfitVO> getAllFundDailyProfitList() {
        Long userId = UserContext.getUserId();

        List<UserFundHold> holdList = userFundHoldMapper.selectList(
                new LambdaQueryWrapper<UserFundHold>()
                        .eq(UserFundHold::getUserId, userId)
                        .gt(UserFundHold::getHoldShares, BigDecimal.ZERO));
        if (holdList.isEmpty()) {
            return Collections.emptyList();
        }

        // 按 fundCode 聚合（同一基金可能分散在多个分组），避免收益明细页重复展示
        Map<String, UserFundHold> aggMap = new java.util.LinkedHashMap<>();
        for (UserFundHold h : holdList) {
            UserFundHold agg = aggMap.get(h.getFundCode());
            if (agg == null) {
                agg = new UserFundHold();
                agg.setFundCode(h.getFundCode());
                agg.setHoldShares(h.getHoldShares());
                agg.setTotalCostAmount(h.getTotalCostAmount());
                agg.setCostPrice(h.getCostPrice());
                aggMap.put(h.getFundCode(), agg);
            } else {
                BigDecimal sumShares = agg.getHoldShares().add(h.getHoldShares());
                BigDecimal sumCost = agg.getTotalCostAmount().add(h.getTotalCostAmount());
                agg.setHoldShares(sumShares);
                agg.setTotalCostAmount(sumCost);
                agg.setCostPrice(sumCost.divide(sumShares, SCALE, ROUNDING_MODE));
            }
        }
        Collection<UserFundHold> aggHolds = aggMap.values();

        // 批量获取所有基金的实时估值
        Map<String, FundRealtimeValuationVO> valuationMap = new java.util.HashMap<>();
        for (String code : aggMap.keySet()) {
            try {
                FundRealtimeValuationVO v = fundRealtimeValuationService.getLatestValuationByFundCode(code);
                if (v != null) valuationMap.put(code, v);
            } catch (Exception e) {
                log.warn("获取基金 {} 实时估值失败: {}", code, e.getMessage());
            }
        }

        // 遍历聚合后的持仓，逐个计算收益
        List<UserFundDailyProfitVO> voList = aggHolds.stream()
                .map(hold -> {
                    FundRealtimeValuationVO valuation = valuationMap.get(hold.getFundCode());
                    if (valuation == null) {
                        return buildFundProfitVOFromOfficialNav(hold);
                    }
                    try {
                        return buildFundProfitVO(hold, valuation);
                    } catch (Exception e) {
                        log.warn("计算基金 {} 每日收益异常: {}", hold.getFundCode(), e.getMessage());
                        return null;
                    }
                })
                .filter(vo -> vo != null)
                .collect(Collectors.toList());
        // 批量存入redis缓存
        dailyProfitCacheService.cacheFundProfitList(userId, voList);

        return voList;
    }

    /**
     * 获取指定分组下持仓基金的今日实时收益（null = 全部分组）
     */
    @Override
    public List<UserFundDailyProfitVO> getAllFundDailyProfitList(Long groupId) {
        Long userId = UserContext.getUserId();

        LambdaQueryWrapper<UserFundHold> wrapper = new LambdaQueryWrapper<UserFundHold>()
                .eq(UserFundHold::getUserId, userId)
                .gt(UserFundHold::getHoldShares, BigDecimal.ZERO);
        if (groupId != null) {
            wrapper.eq(UserFundHold::getGroupId, groupId);
        }
        List<UserFundHold> holdList = userFundHoldMapper.selectList(wrapper);
        if (holdList.isEmpty()) {
            return Collections.emptyList();
        }

        // 按 fundCode 聚合（同一基金可能分散在多个分组）
        Map<String, UserFundHold> aggMap = new java.util.LinkedHashMap<>();
        for (UserFundHold h : holdList) {
            UserFundHold agg = aggMap.get(h.getFundCode());
            if (agg == null) {
                agg = new UserFundHold();
                agg.setFundCode(h.getFundCode());
                agg.setHoldShares(h.getHoldShares());
                agg.setTotalCostAmount(h.getTotalCostAmount());
                agg.setCostPrice(h.getCostPrice());
                aggMap.put(h.getFundCode(), agg);
            } else {
                BigDecimal sumShares = agg.getHoldShares().add(h.getHoldShares());
                BigDecimal sumCost = agg.getTotalCostAmount().add(h.getTotalCostAmount());
                agg.setHoldShares(sumShares);
                agg.setTotalCostAmount(sumCost);
                agg.setCostPrice(sumCost.divide(sumShares, SCALE, ROUNDING_MODE));
            }
        }
        Collection<UserFundHold> aggHolds = aggMap.values();

        // 批量获取实时估值
        Map<String, FundRealtimeValuationVO> valuationMap = new java.util.HashMap<>();
        for (String code : aggMap.keySet()) {
            try {
                FundRealtimeValuationVO v = fundRealtimeValuationService.getLatestValuationByFundCode(code);
                if (v != null) valuationMap.put(code, v);
            } catch (Exception e) {
                log.warn("获取基金 {} 实时估值失败: {}", code, e.getMessage());
            }
        }

        // 逐只计算收益
        List<UserFundDailyProfitVO> voList = aggHolds.stream()
                .map(hold -> {
                    FundRealtimeValuationVO valuation = valuationMap.get(hold.getFundCode());
                    if (valuation == null) {
                        return buildFundProfitVOFromOfficialNav(hold);
                    }
                    try {
                        return buildFundProfitVO(hold, valuation);
                    } catch (Exception e) {
                        log.warn("计算基金 {} 每日收益异常: {}", hold.getFundCode(), e.getMessage());
                        return null;
                    }
                })
                .filter(vo -> vo != null)
                .collect(Collectors.toList());

        return voList;
    }

    /**
     * 获取用户某只基金 的历史收益
     *
     * @param userId
     * @param startDate
     * @param endDate
     * @return
     */
    @Override
    public List<UserFundDailyProfit> getHistoryList(Long userId, LocalDate startDate, LocalDate endDate) {
        return list(new LambdaQueryWrapper<UserFundDailyProfit>()
                .eq(UserFundDailyProfit::getUserId, userId)
                .between(UserFundDailyProfit::getProfitDate, startDate, endDate)
                .orderByDesc(UserFundDailyProfit::getProfitDate)
                .orderByAsc(UserFundDailyProfit::getFundCode));
    }

    /**
     * 构建用户某只基金 的今日收益VO
     * 
     * @param hold
     * @param valuation
     * @return
     */
    private UserFundDailyProfitVO buildFundProfitVO(UserFundHold hold, FundRealtimeValuationVO valuation) {
        UserFundDailyProfitVO vo = new UserFundDailyProfitVO();

        BigDecimal estimateNetValue = valuation.getEstimateNetValue();
        BigDecimal preCloseNetValue = valuation.getPreCloseNetValue();
        BigDecimal holdShares = hold.getHoldShares();

        vo.setFundCode(hold.getFundCode());
        vo.setFundName(valuation.getFundName());
        vo.setFundShortName(valuation.getFundShortName());

        vo.setHoldShares(holdShares);
        vo.setCostPrice(hold.getCostPrice());
        vo.setTotalCostAmount(hold.getTotalCostAmount());

        vo.setPreCloseNetValue(preCloseNetValue);
        vo.setEstimateNetValue(estimateNetValue);
        vo.setEstimateChangeRate(valuation.getEstimateChangeRate());

        BigDecimal marketValue = estimateNetValue.multiply(holdShares).setScale(SCALE, ROUNDING_MODE);
        vo.setMarketValue(marketValue);

        BigDecimal dailyProfit = estimateNetValue.subtract(preCloseNetValue).multiply(holdShares).setScale(SCALE,
                ROUNDING_MODE);
        vo.setDailyProfit(dailyProfit);

        // 用户收益率 = 今日收益 / 持仓成本 * 100（非基金涨跌幅）
        if (hold.getTotalCostAmount() != null && hold.getTotalCostAmount().compareTo(BigDecimal.ZERO) > 0) {
            vo.setDailyProfitRate(dailyProfit.divide(hold.getTotalCostAmount(), 4, ROUNDING_MODE)
                    .multiply(new BigDecimal("100")).setScale(2, ROUNDING_MODE));
        } else {
            vo.setDailyProfitRate(BigDecimal.ZERO);
        }

        return vo;
    }

    public UserFundDailyProfitVO rebuildWithOfficialNAV(UserFundHold hold,
                                                         BigDecimal officialNAV,
                                                         BigDecimal preCloseNAV,
                                                         BigDecimal officialChangeRate) {
        UserFundDailyProfitVO vo = new UserFundDailyProfitVO();
        BigDecimal holdShares = hold.getHoldShares();

        vo.setFundCode(hold.getFundCode());
        vo.setFundName(null);
        vo.setFundShortName(null);

        vo.setHoldShares(holdShares);
        vo.setCostPrice(hold.getCostPrice());
        vo.setTotalCostAmount(hold.getTotalCostAmount());

        vo.setPreCloseNetValue(preCloseNAV);
        vo.setEstimateNetValue(officialNAV);
        vo.setEstimateChangeRate(officialChangeRate);

        BigDecimal marketValue = officialNAV.multiply(holdShares).setScale(SCALE, ROUNDING_MODE);
        vo.setMarketValue(marketValue);

        BigDecimal dailyProfit = officialNAV.subtract(preCloseNAV).multiply(holdShares).setScale(SCALE, ROUNDING_MODE);
        vo.setDailyProfit(dailyProfit);

        if (hold.getTotalCostAmount() != null && hold.getTotalCostAmount().compareTo(BigDecimal.ZERO) > 0) {
            vo.setDailyProfitRate(dailyProfit.divide(hold.getTotalCostAmount(), 4, ROUNDING_MODE)
                    .multiply(new BigDecimal("100")).setScale(2, ROUNDING_MODE));
        } else {
            vo.setDailyProfitRate(BigDecimal.ZERO);
        }

        return vo;
    }

    public FundNetValueHistory getOfficialNAV(String fundCode, LocalDate date) {
        return fundNetValueHistoryMapper.selectOne(
                new LambdaQueryWrapper<FundNetValueHistory>()
                        .eq(FundNetValueHistory::getFundCode, fundCode)
                        .eq(FundNetValueHistory::getNetValueDate, date)
                        .last("LIMIT 1"));
    }

    /**
     * 无实时估值时用官方净值兜底计算（非交易时段/估值不可用时）
     */
    private UserFundDailyProfitVO buildFundProfitVOFromOfficialNav(UserFundHold hold) {
        UserFundDailyProfitVO vo = new UserFundDailyProfitVO();
        vo.setFundCode(hold.getFundCode());
        vo.setHoldShares(hold.getHoldShares());
        vo.setCostPrice(hold.getCostPrice());
        vo.setTotalCostAmount(hold.getTotalCostAmount());

        // 查 fund_base_info 获取最新净值
        FundBaseInfo fund = fundBaseInfoMapper.selectOne(
                new LambdaQueryWrapper<FundBaseInfo>().eq(FundBaseInfo::getFundCode, hold.getFundCode()));
        BigDecimal latestNav = fund != null ? fund.getLatestNetValue() : null;

        // 查 fund_net_value_history 获取前一日净值
        FundNetValueHistory preNav = fundNetValueHistoryMapper.selectOne(
                new LambdaQueryWrapper<FundNetValueHistory>()
                        .eq(FundNetValueHistory::getFundCode, hold.getFundCode())
                        .orderByDesc(FundNetValueHistory::getNetValueDate)
                        .last("LIMIT 1"));
        BigDecimal preClose = preNav != null ? preNav.getUnitNetValue() : null;

        if (latestNav != null && preClose != null && latestNav.compareTo(BigDecimal.ZERO) > 0) {
            vo.setEstimateNetValue(latestNav);
            vo.setPreCloseNetValue(preClose);
            vo.setEstimateChangeRate(fund.getLatestChangeRate());
            vo.setMarketValue(latestNav.multiply(hold.getHoldShares()).setScale(SCALE, ROUNDING_MODE));
            BigDecimal dp = latestNav.subtract(preClose).multiply(hold.getHoldShares()).setScale(SCALE, ROUNDING_MODE);
            vo.setDailyProfit(dp);
            if (hold.getTotalCostAmount() != null && hold.getTotalCostAmount().compareTo(BigDecimal.ZERO) > 0) {
                vo.setDailyProfitRate(dp.divide(hold.getTotalCostAmount(), 4, ROUNDING_MODE)
                        .multiply(new BigDecimal("100")).setScale(2, ROUNDING_MODE));
            } else {
                vo.setDailyProfitRate(BigDecimal.ZERO);
            }
            log.info("基金 {} 使用官方净值兜底计算：净值 {}，前收 {}，收益 {}", hold.getFundCode(), latestNav, preClose, vo.getDailyProfit());
        } else {
            // 实在没数据，市场价值用成本兜底，收益为0
            vo.setDailyProfit(BigDecimal.ZERO);
            vo.setDailyProfitRate(BigDecimal.ZERO);
            vo.setMarketValue(hold.getTotalCostAmount());
            log.warn("基金 {} 官方净值也不可用，收益显示为0", hold.getFundCode());
        }
        return vo;
    }
}
