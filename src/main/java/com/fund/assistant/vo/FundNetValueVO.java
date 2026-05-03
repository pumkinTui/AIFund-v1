package com.fund.assistant.vo;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 基金历史净值
 */
@Data
public class FundNetValueVO {

    private LocalDate netValueDate;
    private BigDecimal unitNetValue;
    private BigDecimal cumulativeNetValue;
    private BigDecimal dailyChangeRate;
}