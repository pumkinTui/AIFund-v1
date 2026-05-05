package com.fund.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 股票实时行情表
 * </p>
 *
 * @author jhshen
 * @since 2026-05-05
 */
@Getter
@Setter
@TableName("stock_realtime_quote")
public class StockRealtimeQuote implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 股票代码
     */
    private String stockCode;

    /**
     * 最新价格
     */
    private BigDecimal latestPrice;

    /**
     * 昨日收盘价
     */
    private BigDecimal preClosePrice;

    /**
     * 涨跌额
     */
    private BigDecimal changeAmount;

    /**
     * 涨跌幅(%)
     */
    private BigDecimal changeRate;

    /**
     * 今日最高价
     */
    private BigDecimal highPrice;

    /**
     * 今日最低价
     */
    private BigDecimal lowPrice;

    /**
     * 成交量(手)
     */
    private Long tradeVolume;

    /**
     * 成交额(元)
     */
    private BigDecimal tradeAmount;

    /**
     * 行情更新时间
     */
    private LocalDateTime quoteTime;

    /**
     * 是否交易中：1=是 0=停牌/休市
     */
    private Byte isTrading;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
