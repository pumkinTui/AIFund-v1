package com.fund.assistant.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FundRealtimeValuationVO {
    private String fundCode;
    private String fundName;
    private String fundShortName;
    private LocalDateTime valuationTime;
    private BigDecimal preCloseNetValue; // 昨日净值
    private BigDecimal estimateNetValue; // 实时估算净值
    private BigDecimal estimateChangeRate; // 估算涨跌幅(%)
    private BigDecimal latestNetValue; // 最新公布净值
    private BigDecimal latestChangeRate; // 最新公布涨跌幅
    private BigDecimal stockPositionRatio; // 股票仓位比例
    private String valuationStatusDesc; // 估值状态描述
}