package com.fund.assistant.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 修改定投计划
 */
@Data
public class FundInvestPlanUpdateDTO {
    private Long planId;
    private BigDecimal investAmount;// 每期投入金额
    private BigDecimal chargeRate;// 手续费率，默认 0%
}