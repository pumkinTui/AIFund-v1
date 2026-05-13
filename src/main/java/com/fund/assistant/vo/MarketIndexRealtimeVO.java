package com.fund.assistant.vo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 大盘指数实时行情
 */
@Data
public class MarketIndexRealtimeVO {
    private String indexCode;
    private String indexName;
    private BigDecimal currentPoint;   // 当前点位
    private BigDecimal changeRate;     // 涨跌幅(%)
    private BigDecimal changePoint;    // 涨跌点
    private String marketType;         // A股/港股/美股
}
