package com.fund.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 基金实时估值结果表
 * </p>
 *
 * @author jhshen
 * @since 2026-05-05
 */
@Getter
@Setter
@TableName("fund_realtime_valuation")
@Data
public class FundRealtimeValuation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 基金代码
     */
    private String fundCode;

    /**
     * 估值时间
     */
    private LocalDateTime valuationTime;

    /**
     * 昨日单位净值
     */
    private BigDecimal preCloseNetValue;

    /**
     * 实时估算净值
     */
    private BigDecimal estimateNetValue;

    /**
     * 估算涨跌幅(%)
     */
    private BigDecimal estimateChangeRate;

    /**
     * 股票仓位比例(%)
     */
    private BigDecimal stockPositionRatio;

    /**
     * 估值状态：1=正常 2=无重仓数据 3=非交易时间
     */
    private Byte valuationStatus;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
