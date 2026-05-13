package com.fund.assistant.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class FundPendingTradeVO {
    private Long id;
    private String fundCode;
    private String fundName;
    private String fundShortName;
    private Byte tradeType;      // 1=买入 2=定投 3=卖出
    private String tradeTypeDesc;
    private BigDecimal tradeAmount;
    private BigDecimal tradeShares;
    private BigDecimal chargeFee;
    private LocalDateTime tradeTime;
    private LocalDate tradeDate;
    private LocalDate confirmDate;
    private Byte status;         // 0=待确认 1=已确认 2=已取消
    private String statusDesc;
    private Long relatedPlanId;
}
