package com.fund.assistant.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 行业板块实时涨跌排行
 */
@Data
public class MarketSectorRealtimeVO {
    private String sectorCode;
    private String sectorName;
    private BigDecimal changeRate;     // 涨跌幅(%)
}
