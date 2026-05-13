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

@Getter
@Setter
@Accessors(chain = true)
@TableName("fund_pending_trade")
public class FundPendingTrade implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("fund_code")
    private String fundCode;

    @TableField("trade_type")
    private Byte tradeType;

    @TableField("trade_amount")
    private BigDecimal tradeAmount;

    @TableField("trade_shares")
    private BigDecimal tradeShares;

    @TableField("charge_fee")
    private BigDecimal chargeFee;

    @TableField("trade_time")
    private LocalDateTime tradeTime;

    @TableField("trade_date")
    private LocalDate tradeDate;

    @TableField("confirm_date")
    private LocalDate confirmDate;

    @TableField("fund_type")
    private Byte fundType;

    @TableField("related_plan_id")
    private Long relatedPlanId;

    @TableField("status")
    private Byte status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
