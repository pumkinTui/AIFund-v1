package com.fund.assistant.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 定投计划
 */
@Data
public class FundInvestPlanVO {

    private Long id;

    private String fundCode;

    private String fundName;

    // 基金简称
    private String fundShortName;

    private Long groupId;

    private String groupName;

    // 每期投入金额
    private BigDecimal investAmount;

    // 手续费率
    private BigDecimal chargeRate;

    // 定投周期：1=每日 2=每周 3=每月
    private Byte investCycle;

    // 定投日期：每周1-7 / 每月1-28
    private Byte cycleDay;

    // 周期中文说明：每日/每周/每月
    private String investCycleDesc;

    // 下次扣款日期
    private LocalDate nextDeductDate;

    // 总投入期数
    private Integer totalInvestPeriod;

    // 总投入金额
    private BigDecimal totalInvestAmount;

    // 计划状态：0=暂停 1=正常 2=已结束
    private Byte planStatus;

    // 状态中文：正常、暂停、已结束
    private String planStatusDesc;

    // 创建时间
    private LocalDateTime createTime;
}