package com.fund.assistant.dto;

import lombok.Data;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class FundHoldSellDTO {
    @NotNull(message = "持仓记录ID不能为空")
    private Long holdId;

    @DecimalMin(value = "0.01", message = "卖出份额不能小于0.01份")
    private BigDecimal sellShares;

    @DecimalMin(value = "0.00", message = "赎回费率不能为负")
    private BigDecimal chargeRate; // 交易费率(%)，对应charge_rate

    @NotNull(message = "交易时点不能为空")
    private Byte tradeTimeFlag; // 1=15点前 2=15点后/非交易日
}