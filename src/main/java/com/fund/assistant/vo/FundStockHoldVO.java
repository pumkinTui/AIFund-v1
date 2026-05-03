package com.fund.assistant.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 基金的重仓股票
 */

@Data
public class FundStockHoldVO {

    // 股票代码
    private String stockCode;

    // 股票名称
    private String stockName;

    // 持仓占比
    private BigDecimal holdRatio;

    // 当前股价
    private BigDecimal stockPrice;

    // 当日涨跌幅
    private BigDecimal dayGrowthRate;

    // 报告日期
    private LocalDate reportDate;
}