package com.fund.assistant.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

/**
 * j交易记录分页查询
 */
@Data
public class TradeQueryDTO {
    // 基金代码
    private String fundCode;
    // 交易类型（1=加仓 2=减仓 3=现金分红 4=红利再投资）
    private Byte tradeType;
    // 交易开始日期
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    // 交易结束日期
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    // 页码
    private Integer pageNum = 1;
    // 每页条数
    private Integer pageSize = 20;
}