package com.fund.assistant.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创建定投计划
 */
@Data
public class FundInvestPlanCreateDTO {

    private String fundCode;
    private Long groupId;
    private BigDecimal investAmount;// 每期投入金额
    private BigDecimal chargeRate;  // 手续费率，默认 0%
    private Byte investCycle;       // 定投周期：1=每日 2=每周 3=每月
    private Byte cycleDay;          // 定投日期：每周1-7 / 每月1-28
    private LocalDate startDate;    // 开始日期  可选  默认今天
}