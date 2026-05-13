package com.fund.assistant.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FundBaseInfoVO {
    private Long id;                    // 基金主键ID
    private String fundCode;            // 基金代码
    private String fundName;            // 基金全称
    private String fundShortName;       // 基金简称
    private String fundType;            // 基金类型（股票型/混合型等）
    private String fundPlate;           // 基金板块（光伏/半导体/白酒）
    private Byte riskLevel;             // 风险等级 1~5
    private String fundManager;         // 基金经理
    private String fundCompany;         // 基金公司
    private LocalDate establishDate;    // 成立日期
    private BigDecimal latestNetValue;  // 最新净值
    private BigDecimal latestChangeRate;// 当日涨跌幅
    private LocalDate netValueDate;     // 净值日期
}