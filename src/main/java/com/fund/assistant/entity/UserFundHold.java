package com.fund.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用户持仓表
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("user_fund_hold")
public class UserFundHold implements Serializable {

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
     * 持有份额
     */
    @TableField("hold_shares")
    private BigDecimal holdShares;

    /**
     * 持仓成本单价
     */
    @TableField("cost_price")
    private BigDecimal costPrice;

    /**
     * 持仓总成本
     */
    @TableField("total_cost_amount")
    private BigDecimal totalCostAmount;

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
