package com.fund.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fund.assistant.dto.FundBuyResultDTO;
import com.fund.assistant.dto.FundHoldBuyDTO;
import com.fund.assistant.dto.FundHoldSellDTO;
import com.fund.assistant.entity.FundBaseInfo;
import com.fund.assistant.entity.FundInvestPlan;
import com.fund.assistant.entity.FundNetValueHistory;
import com.fund.assistant.entity.FundPendingTrade;
import com.fund.assistant.entity.FundTradeRecord;
import com.fund.assistant.entity.FundUserGroup;
import com.fund.assistant.entity.UserFundHold;
import com.fund.assistant.exception.BusinessException;
import com.fund.assistant.mapper.FundBaseInfoMapper;
import com.fund.assistant.mapper.FundInvestPlanMapper;
import com.fund.assistant.mapper.FundNetValueHistoryMapper;
import com.fund.assistant.mapper.FundPendingTradeMapper;
import com.fund.assistant.mapper.FundTradeRecordMapper;
import com.fund.assistant.mapper.UserFundHoldMapper;
import com.fund.assistant.service.FundRealtimeValuationService;
import com.fund.assistant.service.FundUserGroupService;
import com.fund.assistant.service.UserFundHoldService;
import com.fund.assistant.vo.FundRealtimeValuationVO;
import com.fund.assistant.util.TradeTimeUtils;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.UserFundHoldVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserFundHoldServiceImpl extends ServiceImpl<UserFundHoldMapper, UserFundHold> implements UserFundHoldService {

    @Autowired
    private FundBaseInfoMapper fundBaseInfoMapper;

    @Autowired
    private FundUserGroupService fundUserGroupService;


    @Autowired
    private FundTradeRecordMapper fundTradeRecordMapper;

    @Autowired
    private FundPendingTradeMapper fundPendingTradeMapper;

    @Autowired
    private FundNetValueHistoryMapper fundNetValueHistoryMapper;

    @Autowired
    private FundInvestPlanMapper fundInvestPlanMapper;

    @Autowired
    private com.fund.assistant.service.FundRealtimeValuationService fundRealtimeValuationService;

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final byte TRADE_TYPE_ADD = 1;
    private static final byte TRADE_TYPE_REDUCE = 2;
    private static final byte IMPORT_TYPE_MANUAL = 0;
    private static final byte PENDING_STATUS_PENDING = 0;
    private static final byte PENDING_STATUS_CONFIRMED = 1;
    private static final byte PENDING_STATUS_CANCELLED = 2;

    /**
     * 基金买入
     * @param dto
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FundBuyResultDTO buyFund(FundHoldBuyDTO dto) {
        Long userId = UserContext.getUserId();

        LambdaQueryWrapper<FundBaseInfo> fundWrapper = new LambdaQueryWrapper<>();
        fundWrapper.eq(FundBaseInfo::getFundCode, dto.getFundCode());
        FundBaseInfo fund = fundBaseInfoMapper.selectOne(fundWrapper);
        if (fund == null) {
            throw new BusinessException("基金不存在");
        }

        //分组
        Long groupId = dto.getGroupId();
        if (groupId == null) {
            groupId = fundUserGroupService.getOrCreateDefaultHoldGroup(userId);
        } else {
            LambdaQueryWrapper<FundUserGroup> groupWrapper = new LambdaQueryWrapper<>();
            groupWrapper.eq(FundUserGroup::getId, groupId);
            groupWrapper.eq(FundUserGroup::getUserId, userId);
            groupWrapper.eq(FundUserGroup::getGroupType, (byte) 1);
            if (fundUserGroupService.count(groupWrapper) == 0) {
                throw new BusinessException("持仓分组不存在");
            }
        }

        LocalDateTime orderTime = LocalDateTime.now();
        LocalDate tradeDate = TradeTimeUtils.calculateTradeDate(orderTime);
        Byte fundType = TradeTimeUtils.determineFundType(dto.getFundCode(), fund.getFundName());
        LocalDate confirmDate = TradeTimeUtils.calculateConfirmDate(tradeDate, fundType);

        BigDecimal chargeRate = dto.getChargeRate() != null ? dto.getChargeRate() : BigDecimal.ZERO;
        BigDecimal chargeFee = dto.getInvestAmount().multiply(chargeRate).divide(new BigDecimal("100"), SCALE, ROUNDING_MODE);
        BigDecimal netAmount = dto.getInvestAmount().subtract(chargeFee);

        // 预估份额（提前算好，确认时用它替换确认份额，避免多次买入frozen混乱）
        BigDecimal estNetValue = fund.getLatestNetValue() != null && fund.getLatestNetValue().compareTo(BigDecimal.ZERO) > 0
                ? fund.getLatestNetValue() : BigDecimal.ONE;
        BigDecimal estShares = netAmount.divide(estNetValue, SCALE, ROUNDING_MODE);

        FundPendingTrade pendingTrade = new FundPendingTrade();
        pendingTrade.setUserId(userId);
        pendingTrade.setFundCode(dto.getFundCode());
        pendingTrade.setTradeType(dto.getRelatedPlanId() != null ? (byte) 2 : (byte) 1);
        pendingTrade.setTradeAmount(dto.getInvestAmount());
        pendingTrade.setChargeFee(chargeFee);
        pendingTrade.setTradeShares(estShares);
        pendingTrade.setTradeTime(orderTime);
        pendingTrade.setTradeDate(tradeDate);
        pendingTrade.setConfirmDate(confirmDate);
        pendingTrade.setFundType(fundType);
        pendingTrade.setRelatedPlanId(dto.getRelatedPlanId());
        pendingTrade.setStatus(PENDING_STATUS_PENDING);
        pendingTrade.setCreateTime(LocalDateTime.now());
        fundPendingTradeMapper.insert(pendingTrade);

        FundBuyResultDTO result = new FundBuyResultDTO();
        result.setTradeId(pendingTrade.getId());
        result.setChargeFee(chargeFee);
        result.setNetBuyAmount(netAmount);
        result.setTradeDate(tradeDate);
        result.setConfirmDate(confirmDate);

        // 预建持仓：买入后立刻显示在持仓列表，份额冻结直到确认日

        UserFundHold existHold = this.getOne(new LambdaQueryWrapper<UserFundHold>()
                .eq(UserFundHold::getUserId, userId)
                .eq(UserFundHold::getFundCode, dto.getFundCode())
                .eq(UserFundHold::getGroupId, groupId));
        if (existHold != null) {
            // 已有持仓：累加份额，全部冻结
            BigDecimal newShares = existHold.getHoldShares().add(estShares);
            BigDecimal newFrozen = (existHold.getFrozenShares() != null ? existHold.getFrozenShares() : BigDecimal.ZERO).add(estShares);
            BigDecimal newCost = existHold.getTotalCostAmount().add(netAmount);
            BigDecimal newCostPrice = newCost.divide(newShares, SCALE, ROUNDING_MODE);
            existHold.setHoldShares(newShares);
            existHold.setFrozenShares(newFrozen);
            existHold.setCostPrice(newCostPrice);
            existHold.setTotalCostAmount(newCost);
            existHold.setUpdateTime(LocalDateTime.now());
            this.updateById(existHold);
        } else {
            UserFundHold newHold = new UserFundHold();
            newHold.setUserId(userId);
            newHold.setFundCode(dto.getFundCode());
            newHold.setGroupId(groupId);
            newHold.setHoldShares(estShares);
            newHold.setFrozenShares(estShares);
            newHold.setCostPrice(estNetValue);
            newHold.setTotalCostAmount(netAmount);
            newHold.setCreateTime(LocalDateTime.now());
            this.save(newHold);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sellFund(FundHoldSellDTO dto) {
        Long userId = UserContext.getUserId();

        UserFundHold hold = this.getById(dto.getHoldId());
        if (hold == null || !hold.getUserId().equals(userId)) {
            throw new BusinessException("持仓记录不存在");
        }

        BigDecimal frozen = hold.getFrozenShares() != null ? hold.getFrozenShares() : BigDecimal.ZERO;
        BigDecimal availableShares = hold.getHoldShares().subtract(frozen);
        if (dto.getSellShares().compareTo(availableShares) > 0) {
            throw new BusinessException("卖出份额不能大于可用份额（已冻结" + frozen + "份）");
        }

        FundBaseInfo fund = fundBaseInfoMapper.selectOne(
                new LambdaQueryWrapper<FundBaseInfo>().eq(FundBaseInfo::getFundCode, hold.getFundCode())
        );

        LocalDateTime orderTime = LocalDateTime.now();
        LocalDate tradeDate = TradeTimeUtils.calculateTradeDate(orderTime);
        Byte fundType = TradeTimeUtils.determineFundType(hold.getFundCode(), fund != null ? fund.getFundName() : null);
        LocalDate confirmDate = TradeTimeUtils.calculateConfirmDate(tradeDate, fundType);

        BigDecimal chargeRate = dto.getChargeRate() != null ? dto.getChargeRate() : BigDecimal.ZERO;
        BigDecimal chargeFee = dto.getSellShares().multiply(hold.getCostPrice())
                .multiply(chargeRate).divide(new BigDecimal("100"), SCALE, ROUNDING_MODE);

        FundPendingTrade pendingTrade = new FundPendingTrade();
        pendingTrade.setUserId(userId);
        pendingTrade.setFundCode(hold.getFundCode());
        pendingTrade.setTradeType((byte) 3);
        pendingTrade.setTradeAmount(BigDecimal.ZERO);
        pendingTrade.setTradeShares(dto.getSellShares());
        pendingTrade.setChargeFee(chargeFee);
        pendingTrade.setTradeTime(orderTime);
        pendingTrade.setTradeDate(tradeDate);
        pendingTrade.setConfirmDate(confirmDate);
        pendingTrade.setFundType(fundType);
        pendingTrade.setStatus(PENDING_STATUS_PENDING);
        pendingTrade.setCreateTime(LocalDateTime.now());
        fundPendingTradeMapper.insert(pendingTrade);

        BigDecimal newFrozen = frozen.add(dto.getSellShares());
        hold.setFrozenShares(newFrozen);
        hold.setUpdateTime(LocalDateTime.now());
        this.updateById(hold);
    }

    @Override
    public List<UserFundHoldVO> getHoldList(Long groupId) {
        Long userId = UserContext.getUserId();

        LambdaQueryWrapper<UserFundHold> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFundHold::getUserId, userId);
        if (groupId != null) {
            wrapper.eq(UserFundHold::getGroupId, groupId);
        }
        wrapper.gt(UserFundHold::getHoldShares, BigDecimal.ZERO);
        wrapper.orderByDesc(UserFundHold::getUpdateTime);
        List<UserFundHold> holdList = this.list(wrapper);

        if (holdList.isEmpty()) {
            return List.of();
        }

        List<String> fundCodeList = holdList.stream()
                .map(UserFundHold::getFundCode)
                .collect(Collectors.toList());
        LambdaQueryWrapper<FundBaseInfo> fundWrapper = new LambdaQueryWrapper<>();
        fundWrapper.in(FundBaseInfo::getFundCode, fundCodeList);
        List<FundBaseInfo> fundList = fundBaseInfoMapper.selectList(fundWrapper);
        Map<String, FundBaseInfo> fundMap = fundList.stream()
                .collect(Collectors.toMap(FundBaseInfo::getFundCode, fund -> fund));


        List<Long> groupIdList = holdList.stream()
                .map(UserFundHold::getGroupId)
                .distinct()
                .collect(Collectors.toList());
        LambdaQueryWrapper<FundUserGroup> groupWrapper = new LambdaQueryWrapper<>();
        groupWrapper.in(FundUserGroup::getId, groupIdList);
        List<FundUserGroup> groupList = fundUserGroupService.list(groupWrapper);
        Map<Long, String> groupNameMap = groupList.stream()
                .collect(Collectors.toMap(FundUserGroup::getId, FundUserGroup::getGroupName));

        // 按fundCode聚合（全部视图下去重）
        if (groupId == null) {
            Map<String, UserFundHoldVO> agg = new java.util.LinkedHashMap<>();
            for (UserFundHold hold : holdList) {
                String code = hold.getFundCode();
                UserFundHoldVO vo = agg.get(code);
                if (vo == null) {
                    vo = new UserFundHoldVO();
                    BeanUtils.copyProperties(hold, vo);
                    vo.setId(hold.getId());
                    vo.setGroupId(hold.getGroupId());
                    vo.setGroupName(groupNameMap.get(hold.getGroupId()));
                } else {
                    // 累加份额和成本
                    vo.setHoldShares(vo.getHoldShares().add(hold.getHoldShares()));
                    vo.setTotalCostAmount(vo.getTotalCostAmount().add(hold.getTotalCostAmount()));
                    vo.setCostPrice(vo.getTotalCostAmount().divide(vo.getHoldShares(), SCALE, ROUNDING_MODE));
                }
                agg.put(code, vo);
            }
            // 补充基金信息
            for (UserFundHoldVO vo : agg.values()) {
                FundBaseInfo fund = fundMap.get(vo.getFundCode());
                if (fund != null) {
                    vo.setFundName(fund.getFundName());
                    vo.setFundShortName(fund.getFundShortName());
                    vo.setLatestNetValue(fund.getLatestNetValue());
                    BigDecimal cr = fund.getLatestChangeRate();
                    vo.setLatestChangeRate(cr != null ? cr : BigDecimal.ZERO);
                    BigDecimal nav = fund.getLatestNetValue() != null ? fund.getLatestNetValue() : BigDecimal.ONE;
                    BigDecimal mktVal = vo.getHoldShares().multiply(nav).setScale(SCALE, ROUNDING_MODE);
                    BigDecimal profit = mktVal.subtract(vo.getTotalCostAmount()).setScale(SCALE, ROUNDING_MODE);
                    BigDecimal rate = vo.getTotalCostAmount().compareTo(BigDecimal.ZERO) > 0
                            ? profit.divide(vo.getTotalCostAmount(), 4, ROUNDING_MODE).multiply(new BigDecimal("100")).setScale(2, ROUNDING_MODE)
                            : BigDecimal.ZERO;
                    vo.setCurrentMarketValue(mktVal);
                    vo.setProfitAmount(profit);
                    vo.setProfitRate(rate);
                    fillDailyProfit(fund.getFundCode(), vo.getHoldShares(), vo);
                }
            }
            return new ArrayList<>(agg.values());
        }

        // 分组筛选时不聚合
        return holdList.stream().map(hold -> {
            UserFundHoldVO vo = new UserFundHoldVO();
            BeanUtils.copyProperties(hold, vo);
            FundBaseInfo fund = fundMap.get(hold.getFundCode());
            if (fund != null) {
                vo.setFundName(fund.getFundName());
                vo.setFundShortName(fund.getFundShortName());
                vo.setLatestNetValue(fund.getLatestNetValue());
                BigDecimal cr = fund.getLatestChangeRate();
                vo.setLatestChangeRate(cr != null ? cr : BigDecimal.ZERO);
                BigDecimal nav = fund.getLatestNetValue() != null ? fund.getLatestNetValue() : BigDecimal.ONE;
                BigDecimal currentMarketValue = hold.getHoldShares().multiply(nav).setScale(SCALE, ROUNDING_MODE);
                BigDecimal profitAmount = currentMarketValue.subtract(hold.getTotalCostAmount()).setScale(SCALE, ROUNDING_MODE);
                BigDecimal profitRate = hold.getTotalCostAmount().compareTo(BigDecimal.ZERO) > 0
                        ? profitAmount.divide(hold.getTotalCostAmount(), 4, ROUNDING_MODE).multiply(new BigDecimal("100")).setScale(2, ROUNDING_MODE)
                        : BigDecimal.ZERO;
                vo.setCurrentMarketValue(currentMarketValue);
                vo.setProfitAmount(profitAmount);
                vo.setProfitRate(profitRate);
                fillDailyProfit(fund.getFundCode(), hold.getHoldShares(), vo);
            }
            vo.setGroupName(groupNameMap.get(hold.getGroupId()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public UserFundHoldVO getHoldDetail(String fundCode) {
        Long userId = UserContext.getUserId();

        LambdaQueryWrapper<UserFundHold> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFundHold::getUserId, userId);
        wrapper.eq(UserFundHold::getFundCode, fundCode);
        wrapper.gt(UserFundHold::getHoldShares, BigDecimal.ZERO);
        wrapper.orderByDesc(UserFundHold::getUpdateTime);
        wrapper.last("LIMIT 1");
        UserFundHold hold = this.getOne(wrapper);

        if (hold == null) {
            return null;
        }

        UserFundHoldVO vo = new UserFundHoldVO();
        BeanUtils.copyProperties(hold, vo);

        LambdaQueryWrapper<FundBaseInfo> fundWrapper = new LambdaQueryWrapper<>();
        fundWrapper.eq(FundBaseInfo::getFundCode, fundCode);
        FundBaseInfo fund = fundBaseInfoMapper.selectOne(fundWrapper);
        if (fund != null) {
            vo.setFundName(fund.getFundName());
            vo.setFundShortName(fund.getFundShortName());
            vo.setLatestNetValue(fund.getLatestNetValue());
            vo.setLatestChangeRate(fund.getLatestChangeRate() != null ? fund.getLatestChangeRate() : BigDecimal.ZERO);

            BigDecimal nav = fund.getLatestNetValue() != null ? fund.getLatestNetValue() : BigDecimal.ONE;
            BigDecimal currentMarketValue = hold.getHoldShares().multiply(nav).setScale(SCALE, ROUNDING_MODE);
            BigDecimal profitAmount = currentMarketValue.subtract(hold.getTotalCostAmount()).setScale(SCALE, ROUNDING_MODE);
            BigDecimal profitRate;
            if (hold.getTotalCostAmount().compareTo(BigDecimal.ZERO) > 0) {
                profitRate = profitAmount.divide(hold.getTotalCostAmount(), 4, ROUNDING_MODE).multiply(new BigDecimal("100")).setScale(2, ROUNDING_MODE);
            } else {
                profitRate = BigDecimal.ZERO;
            }

            vo.setCurrentMarketValue(currentMarketValue);
            vo.setProfitAmount(profitAmount);
            vo.setProfitRate(profitRate);

            fillDailyProfit(fundCode, hold.getHoldShares(), vo);
        }

        return vo;
    }

    /** 当日收益：优先用实时估值计算（今日估算净值-昨日收盘净值）×份额，同时用Redis实时估值覆盖净值、涨跌幅、市值、持有收益 */
    private void fillDailyProfit(String fundCode, BigDecimal holdShares, UserFundHoldVO vo) {
        try {
            FundRealtimeValuationVO valuation = fundRealtimeValuationService.getLatestValuationByFundCode(fundCode);
            if (valuation != null && valuation.getEstimateNetValue() != null && valuation.getPreCloseNetValue() != null) {
                // 今日收益 = (估算净值 - 昨日收盘净值) × 持有份额
                BigDecimal dailyProfit = valuation.getEstimateNetValue().subtract(valuation.getPreCloseNetValue())
                        .multiply(holdShares).setScale(SCALE, ROUNDING_MODE);
                vo.setDailyProfit(dailyProfit);
                // 用Redis实时估值覆盖净值、涨跌幅、市值、持有收益，确保与基金详情页一致
                BigDecimal realtimeNav = valuation.getEstimateNetValue();
                vo.setLatestNetValue(realtimeNav);
                if (valuation.getEstimateChangeRate() != null) {
                    vo.setLatestChangeRate(valuation.getEstimateChangeRate());
                }
                BigDecimal realtimeMktVal = holdShares.multiply(realtimeNav).setScale(SCALE, ROUNDING_MODE);
                vo.setCurrentMarketValue(realtimeMktVal);
                if (vo.getTotalCostAmount() != null && vo.getTotalCostAmount().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal realtimeProfit = realtimeMktVal.subtract(vo.getTotalCostAmount()).setScale(SCALE, ROUNDING_MODE);
                    vo.setProfitAmount(realtimeProfit);
                    BigDecimal realtimeRate = realtimeProfit.divide(vo.getTotalCostAmount(), 4, ROUNDING_MODE)
                            .multiply(new BigDecimal("100")).setScale(2, ROUNDING_MODE);
                    vo.setProfitRate(realtimeRate);
                }
                return;
            }
        } catch (Exception e) {
            log.warn("获取实时估值计算当日收益失败，fundCode={}", fundCode, e);
        }
        // 降级：从历史净值表取最近两条计算
        List<FundNetValueHistory> recentNavs = fundNetValueHistoryMapper.selectList(
                new LambdaQueryWrapper<FundNetValueHistory>()
                        .eq(FundNetValueHistory::getFundCode, fundCode)
                        .orderByDesc(FundNetValueHistory::getNetValueDate)
                        .last("LIMIT 2"));
        if (recentNavs.size() >= 2) {
            BigDecimal todayNav = recentNavs.get(0).getUnitNetValue();
            BigDecimal yesterdayNav = recentNavs.get(1).getUnitNetValue();
            BigDecimal dailyProfit = todayNav.subtract(yesterdayNav).multiply(holdShares).setScale(SCALE, ROUNDING_MODE);
            vo.setDailyProfit(dailyProfit);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmPendingTrade(FundPendingTrade trade) {
        BigDecimal confirmNetValue = getNetValueByDate(trade.getFundCode(), trade.getTradeDate());
        if (confirmNetValue == null || confirmNetValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("基金 " + trade.getFundCode() + " 在交易日 " + trade.getTradeDate() + " 无净值数据");
        }

        if (trade.getTradeType() == 1 || trade.getTradeType() == 2) {
            BigDecimal actualAmount = trade.getTradeAmount().subtract(trade.getChargeFee());
            BigDecimal confirmShares = actualAmount.divide(confirmNetValue, SCALE, ROUNDING_MODE);

            Long userId = trade.getUserId();
            LambdaQueryWrapper<UserFundHold> holdWrapper = new LambdaQueryWrapper<>();
            holdWrapper.eq(UserFundHold::getUserId, userId);
            holdWrapper.eq(UserFundHold::getFundCode, trade.getFundCode());
            UserFundHold existHold = this.getOne(holdWrapper);

            if (existHold != null) {
                BigDecimal frozen = existHold.getFrozenShares() != null ? existHold.getFrozenShares() : BigDecimal.ZERO;
                if (frozen.compareTo(BigDecimal.ZERO) > 0) {
                    // 预建持仓：用确认份额替换预估份额，解冻
                    // buyFund 时已将 estShares 存入 tradeShares，并累加进 holdShares/frozen/totalCost
                    BigDecimal estShares = trade.getTradeShares();
                    if (estShares == null || estShares.compareTo(BigDecimal.ZERO) <= 0) {
                        estShares = confirmShares; // 兼容旧数据：回退到确认份额
                    }
                    BigDecimal remainFrozen = frozen.subtract(estShares).max(BigDecimal.ZERO);
                    BigDecimal actualShares = existHold.getHoldShares().subtract(estShares).add(confirmShares);
                    existHold.setHoldShares(actualShares.max(BigDecimal.ONE));
                    existHold.setFrozenShares(remainFrozen);
                    // 成本基准不覆盖（buyFund 预建时已正确累加 totalCostAmount）
                    // 重算 costPrice = 总成本 / 总份额
                    if (actualShares.compareTo(BigDecimal.ZERO) > 0) {
                        existHold.setCostPrice(existHold.getTotalCostAmount().divide(actualShares, SCALE, ROUNDING_MODE));
                    }
                } else {
                    // 旧流程（无预售份额）：直接累加
                    BigDecimal newTotalCost = existHold.getTotalCostAmount().add(actualAmount);
                    BigDecimal newTotalShares = existHold.getHoldShares().add(confirmShares);
                    BigDecimal newCostPrice = newTotalCost.divide(newTotalShares, SCALE, ROUNDING_MODE);
                    existHold.setHoldShares(newTotalShares);
                    existHold.setCostPrice(newCostPrice);
                    existHold.setTotalCostAmount(newTotalCost);
                }
                existHold.setUpdateTime(LocalDateTime.now());
                this.updateById(existHold);
            } else {
                UserFundHold newHold = new UserFundHold();
                newHold.setUserId(userId);
                newHold.setFundCode(trade.getFundCode());
                newHold.setHoldShares(confirmShares);
                newHold.setCostPrice(confirmNetValue);
                newHold.setTotalCostAmount(actualAmount);
                newHold.setFrozenShares(BigDecimal.ZERO);
                newHold.setCreateTime(LocalDateTime.now());
                this.save(newHold);
            }

            FundTradeRecord tradeRecord = new FundTradeRecord();
            tradeRecord.setUserId(userId);
            tradeRecord.setFundCode(trade.getFundCode());
            tradeRecord.setTradeType(TRADE_TYPE_ADD);
            tradeRecord.setTradeAmount(trade.getTradeAmount());
            tradeRecord.setTradeShares(confirmShares);
            tradeRecord.setChargeFee(trade.getChargeFee());
            tradeRecord.setTradeDate(trade.getTradeDate());
            tradeRecord.setImportType(IMPORT_TYPE_MANUAL);
            fundTradeRecordMapper.insert(tradeRecord);

            if (trade.getTradeType() == 2 && trade.getRelatedPlanId() != null) {
                FundInvestPlan plan = fundInvestPlanMapper.selectById(trade.getRelatedPlanId());
                if (plan != null) {
                    plan.setTotalInvestPeriod(plan.getTotalInvestPeriod() + 1);
                    plan.setTotalInvestAmount(plan.getTotalInvestAmount().add(trade.getTradeAmount()));
                    plan.setUpdateTime(LocalDateTime.now());
                    fundInvestPlanMapper.updateById(plan);
                }
            }
        } else if (trade.getTradeType() == 3) {
            Long userId = trade.getUserId();
            UserFundHold hold = this.getOne(new LambdaQueryWrapper<UserFundHold>()
                    .eq(UserFundHold::getUserId, userId)
                    .eq(UserFundHold::getFundCode, trade.getFundCode()));

            if (hold == null) {
                throw new BusinessException("持仓不存在，无法确认卖出");
            }

            BigDecimal sharesToSell = trade.getTradeShares();
            BigDecimal frozen = hold.getFrozenShares() != null ? hold.getFrozenShares() : BigDecimal.ZERO;

            BigDecimal redeemAmount = sharesToSell.multiply(confirmNetValue).setScale(SCALE, ROUNDING_MODE);
            BigDecimal actualAmount = redeemAmount.subtract(trade.getChargeFee());

            BigDecimal remainShares = hold.getHoldShares().subtract(sharesToSell);
            BigDecimal newFrozen = frozen.subtract(sharesToSell);

            if (remainShares.compareTo(BigDecimal.ZERO) <= 0) {
                this.removeById(hold.getId());
            } else {
                BigDecimal remainCostAmount = remainShares.multiply(hold.getCostPrice()).setScale(SCALE, ROUNDING_MODE);
                hold.setHoldShares(remainShares);
                hold.setTotalCostAmount(remainCostAmount);
                hold.setFrozenShares(newFrozen.compareTo(BigDecimal.ZERO) > 0 ? newFrozen : BigDecimal.ZERO);
                hold.setUpdateTime(LocalDateTime.now());
                this.updateById(hold);
            }

            FundTradeRecord tradeRecord = new FundTradeRecord();
            tradeRecord.setUserId(userId);
            tradeRecord.setFundCode(trade.getFundCode());
            tradeRecord.setTradeType(TRADE_TYPE_REDUCE);
            tradeRecord.setTradeAmount(actualAmount);
            tradeRecord.setTradeShares(sharesToSell);
            tradeRecord.setChargeFee(trade.getChargeFee());
            tradeRecord.setTradeDate(trade.getTradeDate());
            tradeRecord.setImportType(IMPORT_TYPE_MANUAL);
            fundTradeRecordMapper.insert(tradeRecord);
        }

        trade.setStatus(PENDING_STATUS_CONFIRMED);
        trade.setUpdateTime(LocalDateTime.now());
        fundPendingTradeMapper.updateById(trade);
    }

    private BigDecimal getNetValueByDate(String fundCode, LocalDate date) {
        FundNetValueHistory history = fundNetValueHistoryMapper.selectOne(
                new LambdaQueryWrapper<FundNetValueHistory>()
                        .eq(FundNetValueHistory::getFundCode, fundCode)
                        .eq(FundNetValueHistory::getNetValueDate, date)
                        .last("LIMIT 1"));
        return history != null ? history.getUnitNetValue() : null;
    }
}
