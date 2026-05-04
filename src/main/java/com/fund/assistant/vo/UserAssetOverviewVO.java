package com.fund.assistant.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 资产总览
 */
@Data
public class UserAssetOverviewVO {
    // 核心资产数据
    private BigDecimal totalAsset; // 总资产（当前持仓市值 + 累计已实现收益）
    private BigDecimal totalHoldMarketValue; // 当前持仓总市值
    private BigDecimal totalCostAmount; // 累计投入总成本
    private BigDecimal totalSellAmount; // 累计卖出总回款
    private BigDecimal totalChargeFee; // 累计手续费
    private BigDecimal totalProfitAmount; // 累计总收益（持仓浮盈 + 已实现收益）
    private BigDecimal totalProfitRate; // 累计总收益率(%)
    
    // 持仓分析数据
    private Integer holdFundCount; // 持有基金数量
    private Integer holdGroupCount; // 持仓分组数量
    private Integer totalTradeCount; // 累计交易次数
}