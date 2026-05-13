package com.fund.assistant.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FundBuyResultDTO {

    private Long tradeId;
    private BigDecimal buyNetValue;
    private BigDecimal buyShares;
    private BigDecimal chargeFee;
    private BigDecimal netBuyAmount;
    private LocalDate tradeDate;
    private LocalDate confirmDate;
}