package com.fund.assistant.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 基金定投的执行记录
 */
@Data
public class FundInvestExecRecordVO {

    private Long id;

    private Long planId;

    private String fundCode;

    private String fundName;

    private String fundShortName;

    // 扣款日期（哪天执行的定投）
    private LocalDate deductDate;

    // 扣款金额（投了多少钱）
    private BigDecimal deductAmount;

    // 手续费（扣了多少手续费）
    private BigDecimal chargeFee;

    // 确认份额（买到了多少份基金）
    private BigDecimal confirmShares;

    // 执行状态（数字代码：1=成功 2=失败 3=处理中）
    private Byte execStatus;

    // 执行状态中文描述（如：执行成功、执行失败、扣款中）
    private String execStatusDesc;

    // 这条记录创建的时间
    private LocalDateTime createTime;
}