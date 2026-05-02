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
 * 基金历史净值表
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("fund_net_value_history")
public class FundNetValueHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 基金代码
     */
    @TableField("fund_code")
    private String fundCode;

    /**
     * 净值日期
     */
    @TableField("net_value_date")
    private LocalDate netValueDate;

    /**
     * 单位净值
     */
    @TableField("unit_net_value")
    private BigDecimal unitNetValue;

    /**
     * 累计净值
     */
    @TableField("cumulative_net_value")
    private BigDecimal cumulativeNetValue;

    /**
     * 日涨跌幅(%)
     */
    @TableField("daily_change_rate")
    private BigDecimal dailyChangeRate;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
