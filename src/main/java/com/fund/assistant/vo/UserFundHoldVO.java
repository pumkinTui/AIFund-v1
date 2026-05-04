package com.fund.assistant.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

//持仓列表
@Data
public class UserFundHoldVO {
    private Long id;
    private Long groupId;
    private String groupName;
    private String fundCode;
    private String fundName;
    private String fundShortName;
    private BigDecimal holdShares; // 持有份额
    private BigDecimal costPrice; // 持仓成本单价
    private BigDecimal totalCostAmount; // 持仓总成本
    private BigDecimal latestNetValue; // 最新净值
    private BigDecimal latestChangeRate; // 最新涨跌幅
    private BigDecimal currentMarketValue; // 当前市值
    private BigDecimal profitAmount; // 持仓收益
    private BigDecimal profitRate; // 持仓收益率
    private LocalDateTime createTime;
}