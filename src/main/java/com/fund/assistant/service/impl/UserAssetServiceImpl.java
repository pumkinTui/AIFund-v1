package com.fund.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fund.assistant.entity.FundBaseInfo;
import com.fund.assistant.entity.FundTradeRecord;
import com.fund.assistant.entity.FundUserGroup;
import com.fund.assistant.entity.UserFundHold;
import com.fund.assistant.mapper.FundBaseInfoMapper;
import com.fund.assistant.mapper.FundTradeRecordMapper;
import com.fund.assistant.mapper.FundUserGroupMapper;
import com.fund.assistant.mapper.UserFundHoldMapper;
import com.fund.assistant.service.UserAssetService;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.UserAssetOverviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserAssetServiceImpl implements UserAssetService {

    @Autowired
    private UserFundHoldMapper userFundHoldMapper;

    @Autowired
    private FundTradeRecordMapper fundTradeRecordMapper;

    @Autowired
    private FundBaseInfoMapper fundBaseInfoMapper;

    @Autowired
    private FundUserGroupMapper fundUserGroupMapper;

    // 小数精度
    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    // 交易类型常量
    private static final byte TRADE_TYPE_ADD = 1;
    private static final byte TRADE_TYPE_REDUCE = 2;
    private static final byte TRADE_TYPE_DIVIDEND_CASH = 3;
    private static final byte TRADE_TYPE_DIVIDEND_REINVEST = 4;
    // 分组类型常量
    private static final byte GROUP_TYPE_HOLD = 1;

    @Override
    public UserAssetOverviewVO getAssetOverview() {
        Long userId = UserContext.getUserId();
        UserAssetOverviewVO vo = new UserAssetOverviewVO();

        // 1. 计算持仓相关数据
        LambdaQueryWrapper<UserFundHold> holdWrapper = new LambdaQueryWrapper<>();
        holdWrapper.eq(UserFundHold::getUserId, userId);
        holdWrapper.gt(UserFundHold::getHoldShares, BigDecimal.ZERO);
        List<UserFundHold> holdList = userFundHoldMapper.selectList(holdWrapper);

        BigDecimal totalHoldMarketValue = BigDecimal.ZERO;
        BigDecimal totalHoldCostAmount = BigDecimal.ZERO;
        BigDecimal totalHoldFloatProfit = BigDecimal.ZERO;
        Integer holdFundCount = 0;

        if (!holdList.isEmpty()) {
            holdFundCount = holdList.size();
            // 批量查询基金最新净值
            List<String> fundCodeList = holdList.stream()
                    .map(UserFundHold::getFundCode)
                    .collect(Collectors.toList());
            LambdaQueryWrapper<FundBaseInfo> fundWrapper = new LambdaQueryWrapper<>();
            fundWrapper.in(FundBaseInfo::getFundCode, fundCodeList);
            List<FundBaseInfo> fundList = fundBaseInfoMapper.selectList(fundWrapper);
            Map<String, FundBaseInfo> fundMap = fundList.stream()
                    .collect(Collectors.toMap(FundBaseInfo::getFundCode, f -> f));

            // 计算持仓总市值、总成本、总浮盈
            for (UserFundHold hold : holdList) {
                FundBaseInfo fund = fundMap.get(hold.getFundCode());
                if (fund != null && fund.getLatestNetValue() != null) {
                    BigDecimal marketValue = hold.getHoldShares().multiply(fund.getLatestNetValue()).setScale(SCALE, ROUNDING_MODE);
                    BigDecimal floatProfit = marketValue.subtract(hold.getTotalCostAmount()).setScale(SCALE, ROUNDING_MODE);

                    totalHoldMarketValue = totalHoldMarketValue.add(marketValue);
                    totalHoldCostAmount = totalHoldCostAmount.add(hold.getTotalCostAmount());
                    totalHoldFloatProfit = totalHoldFloatProfit.add(floatProfit);
                }
            }
        }

        vo.setTotalHoldMarketValue(totalHoldMarketValue);
        vo.setHoldFundCount(holdFundCount);

        //2. 计算交易相关数据
        LambdaQueryWrapper<FundTradeRecord> tradeWrapper = new LambdaQueryWrapper<>();
        tradeWrapper.eq(FundTradeRecord::getUserId, userId);
        List<FundTradeRecord> tradeList = fundTradeRecordMapper.selectList(tradeWrapper);

        BigDecimal totalAddAmount = BigDecimal.ZERO; // 累计净投入（加仓+红利再投资 - 手续费）
        BigDecimal totalSellAmount = BigDecimal.ZERO; // 累计回款（减仓+现金分红）
        BigDecimal totalChargeFee = BigDecimal.ZERO; // 累计手续费
        Integer totalTradeCount = 0;

        if (!tradeList.isEmpty()) {
            totalTradeCount = tradeList.size();
            for (FundTradeRecord record : tradeList) {
                // 累计手续费（所有交易的手续费累加）
                totalChargeFee = totalChargeFee.add(record.getChargeFee());

                if (record.getTradeType() == TRADE_TYPE_ADD || record.getTradeType() == TRADE_TYPE_DIVIDEND_REINVEST) {
                    //累计净投入 = 交易金额 - 手续费（不含手续费的真实投入）
                    BigDecimal netAmount = record.getTradeAmount().subtract(record.getChargeFee());
                    totalAddAmount = totalAddAmount.add(netAmount);
                } else if (record.getTradeType() == TRADE_TYPE_REDUCE || record.getTradeType() == TRADE_TYPE_DIVIDEND_CASH) {
                    // 累计回款（减仓+现金分红，直接累加）
                    totalSellAmount = totalSellAmount.add(record.getTradeAmount());
                }
            }
        }

        vo.setTotalSellAmount(totalSellAmount);
        vo.setTotalChargeFee(totalChargeFee);
        vo.setTotalTradeCount(totalTradeCount);

        // 3. 计算收益数据
        // 已实现收益 = 累计回款 - (累计净投入 - 当前持仓成本)
        BigDecimal sellRealProfit = totalSellAmount.subtract(totalAddAmount.subtract(totalHoldCostAmount)).setScale(SCALE, ROUNDING_MODE);
        // 累计总收益 = 持仓浮盈 + 已实现收益
        BigDecimal totalProfitAmount = totalHoldFloatProfit.add(sellRealProfit).setScale(SCALE, ROUNDING_MODE);

        // 【修复2】总资产 = 当前持仓市值 + 累计回款（已落袋的钱）
        BigDecimal totalAsset = totalHoldMarketValue.add(totalSellAmount).setScale(SCALE, ROUNDING_MODE);

        // 累计投入成本（用于计算收益率，用净投入）
        BigDecimal totalCostAmount = totalAddAmount;

        vo.setTotalAsset(totalAsset);
        vo.setTotalCostAmount(totalCostAmount);
        vo.setTotalProfitAmount(totalProfitAmount);

        // 计算总收益率
        if (totalCostAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalProfitRate = totalProfitAmount.divide(totalCostAmount, 4, ROUNDING_MODE)
                    .multiply(new BigDecimal("100")).setScale(2, ROUNDING_MODE);
            vo.setTotalProfitRate(totalProfitRate);
        } else {
            vo.setTotalProfitRate(BigDecimal.ZERO);
        }

        // 4. 计算分组数据
        LambdaQueryWrapper<FundUserGroup> groupWrapper = new LambdaQueryWrapper<>();
        groupWrapper.eq(FundUserGroup::getUserId, userId);
        groupWrapper.eq(FundUserGroup::getGroupType, GROUP_TYPE_HOLD);
        Integer holdGroupCount = Math.toIntExact(fundUserGroupMapper.selectCount(groupWrapper));
        vo.setHoldGroupCount(holdGroupCount);

        return vo;
    }
}