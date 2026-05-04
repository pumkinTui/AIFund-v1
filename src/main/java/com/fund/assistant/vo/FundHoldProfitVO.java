package com.fund.assistant.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 持仓收益明细
 */
@Data
public class FundHoldProfitVO {
    private String fundCode;
    private String fundName;
    private String fundShortName;
    private BigDecimal holdShares; // 当前持有份额
    private BigDecimal totalCostAmount; // 累计投入成本
    private BigDecimal totalSellAmount; // 累计卖出回款
    private BigDecimal totalChargeFee; // 累计手续费
    private BigDecimal currentMarketValue; // 当前持仓市值
    private BigDecimal totalProfitAmount; // 累计总收益（含已卖出+持仓浮盈）
    private BigDecimal totalProfitRate; // 累计总收益率
    private BigDecimal holdFloatProfit; // 持仓浮盈
    private BigDecimal sellRealProfit; // 已卖出实现收益
}