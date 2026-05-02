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
 * 指数每日行情表
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("market_index_daily")
public class MarketIndexDaily implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 指数代码
     */
    @TableField("index_code")
    private String indexCode;

    /**
     * 交易日
     */
    @TableField("trade_date")
    private LocalDate tradeDate;

    /**
     * 开盘点位
     */
    @TableField("open_point")
    private BigDecimal openPoint;

    /**
     * 收盘点位
     */
    @TableField("close_point")
    private BigDecimal closePoint;

    /**
     * 最高点位
     */
    @TableField("highest_point")
    private BigDecimal highestPoint;

    /**
     * 最低点位
     */
    @TableField("lowest_point")
    private BigDecimal lowestPoint;

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
