package com.fund.assistant.vo;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class UserFundDailyProfitVO {

    private String fundCode;
    private String fundName;
    private String fundShortName;
    //  持有份额
    private BigDecimal holdShares;
    // 持仓成本价
    private BigDecimal costPrice;
    // 累计投入总成本（份额 × 成本价）
    private BigDecimal totalCostAmount;
    //  昨日收盘净值
    private BigDecimal preCloseNetValue;
    // 今日估算净值
    private BigDecimal estimateNetValue;
    // 今日估算涨跌幅
    private BigDecimal estimateChangeRate;
    // 当前持仓总市值（持有份额 × 当前净值）
    private BigDecimal marketValue;
    //  今日当日收益
    private BigDecimal dailyProfit;
    //  今日当日收益率
    private BigDecimal dailyProfitRate;
}
