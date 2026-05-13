package com.fund.assistant.vo;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class UserDailyProfitVO {

    // 收益日期：哪一天的收益
    private LocalDate profitDate;

    // 今日总收益
    private BigDecimal totalProfit;

    // 今日总收益率（%）
    private BigDecimal totalProfitRate;

    // 当日总资产
    private BigDecimal totalAsset;

    // 累计持仓总成本
    private BigDecimal totalCostAmount;

    // 持有基金的数量
    private Integer holdFundCount;

    // 每只基金的详细收益列表
    private List<UserFundDailyProfitVO> fundProfitList;
}