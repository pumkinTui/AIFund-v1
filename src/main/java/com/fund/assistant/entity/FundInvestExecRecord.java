package com.fund.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * <p>
 * 定投执行记录表
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("fund_invest_exec_record")
public class FundInvestExecRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 定投计划ID
     */
    @TableField("plan_id")
    private Long planId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 扣款日期
     */
    @TableField("deduct_date")
    private LocalDate deductDate;

    /**
     * 扣款金额
     */
    @TableField("deduct_amount")
    private BigDecimal deductAmount;

    /**
     * 手续费
     */
    @TableField("charge_fee")
    private BigDecimal chargeFee;

    /**
     * 确认份额
     */
    @TableField("confirm_shares")
    private BigDecimal confirmShares;

    /**
     * 执行状态：0=成功 1=失败
     */
    @TableField("exec_status")
    private Byte execStatus;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
