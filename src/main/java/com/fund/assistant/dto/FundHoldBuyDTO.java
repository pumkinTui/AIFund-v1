package com.fund.assistant.dto;

import lombok.Data;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class FundHoldBuyDTO {
    @NotBlank(message = "基金代码不能为空")
    private String fundCode;

    private Long groupId; // 可选，不传则加入默认持仓分组

    @DecimalMin(value = "0.01", message = "投入金额不能小于0.01元")
    private BigDecimal investAmount; // 投入金额

    @DecimalMin(value = "0.00", message = "申购费率不能为负")
    private BigDecimal chargeRate; // 交易费率(%)，对应charge_rate

    @NotNull(message = "交易时点不能为空")
    private Byte tradeTimeFlag; // 1=15点前 2=15点后/非交易日
}