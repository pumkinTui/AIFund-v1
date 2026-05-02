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
 * 单只基金每日收益明细表
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("user_fund_daily_profit")
public class UserFundDailyProfit implements Serializable {

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
     * 收益日期
     */
    @TableField("profit_date")
    private LocalDate profitDate;

    /**
     * 当日收益
     */
    @TableField("daily_profit")
    private BigDecimal dailyProfit;

    /**
     * 当日收益率(%)
     */
    @TableField("daily_profit_rate")
    private BigDecimal dailyProfitRate;

    /**
     * 当日持仓市值
     */
    @TableField("hold_market_value")
    private BigDecimal holdMarketValue;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
