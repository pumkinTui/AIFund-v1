package com.fund.assistant.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 基金交易记录视图对象
 */
@Data
public class TradeRecordVO {

    private Long id;

    // 基金代码、基金全称、基金简称
    private String fundCode;
    private String fundName;
    private String fundShortName;


    private Long groupId;
    private String groupName;

    // 1=加仓 2=减仓 3=现金分红 4=红利再投资
    private Byte tradeType;
    //加仓、减仓、现金分红、红利再投资
    private String tradeTypeDesc;

    // 交易金额：买入花的钱 / 卖出到手金额 / 分红金额
    private BigDecimal tradeAmount;
    // 交易份额：买入多少份 / 卖出多少份 / 再投资分到的份额
    private BigDecimal tradeShares;

    // 手续费率
    private BigDecimal chargeRate;
    // 实际扣除的手续费金额
    private BigDecimal chargeFee;

    // 交易日：yyyy-MM-dd 基金确认交易日
    private LocalDate tradeDate;
    // 交易时间标识：1=15点前  2=15点后
    private Byte tradeTimeFlag;
    // 时点中文：15点前(按当日净值)、15点后(按下一交易日净值)
    private String tradeTimeFlagDesc;


    //记录创建时间
    private LocalDateTime createTime;

}