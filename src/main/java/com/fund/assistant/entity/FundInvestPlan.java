package com.fund.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * <p>
 * 定投计划表
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("fund_invest_plan")
@Data
public class FundInvestPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 基金代码
     */
    @TableField("fund_code")
    private String fundCode;

    /**
     * 所属分组ID
     */
    @TableField("group_id")
    private Long groupId;

    /**
     * 每期定投金额
     */
    @TableField("invest_amount")
    private BigDecimal investAmount;

    /**
     * 申购费率(%)
     */
    @TableField("charge_rate")
    private BigDecimal chargeRate;

    /**
     * 定投周期：1=每日 2=每周 3=每月
     */
    @TableField("invest_cycle")
    private Byte investCycle;

    /**
     * 定投日期：每周1-7 / 每月1-28
     */
    @TableField("cycle_day")
    private Byte cycleDay;

    /**
     * 下次扣款日期
     */
    @TableField("next_deduct_date")
    private LocalDate nextDeductDate;

    /**
     * 累计定投期数
     */
    @TableField("total_invest_period")
    private Integer totalInvestPeriod;

    /**
     * 累计定投总金额
     */
    @TableField("total_invest_amount")
    private BigDecimal totalInvestAmount;

    /**
     * 计划状态：0=进行中 1=已暂停 2=已终止
     */
    @TableField("plan_status")
    private Byte planStatus;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
