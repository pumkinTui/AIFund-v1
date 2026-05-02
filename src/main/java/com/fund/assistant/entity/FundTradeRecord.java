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
 * 基金交易记录表
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("fund_trade_record")
public class FundTradeRecord implements Serializable {

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
     * 交易类型：1=加仓 2=减仓 3=现金分红 4=红利再投资
     */
    @TableField("trade_type")
    private Byte tradeType;

    /**
     * 交易金额
     */
    @TableField("trade_amount")
    private BigDecimal tradeAmount;

    /**
     * 交易份额
     */
    @TableField("trade_shares")
    private BigDecimal tradeShares;

    /**
     * 交易费率(%)
     */
    @TableField("charge_rate")
    private BigDecimal chargeRate;

    /**
     * 手续费
     */
    @TableField("charge_fee")
    private BigDecimal chargeFee;

    /**
     * 交易日期
     */
    @TableField("trade_date")
    private LocalDate tradeDate;

    /**
     * 交易时点：1=15点前 2=15点后/非交易日
     */
    @TableField("trade_time_flag")
    private Byte tradeTimeFlag;

    /**
     * 分红方式：1=现金 2=再投资
     */
    @TableField("dividend_type")
    private Byte dividendType;

    /**
     * 导入方式：0=手动 1=图片识别
     */
    @TableField("import_type")
    private Byte importType;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
