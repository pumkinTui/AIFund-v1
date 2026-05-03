package com.fund.assistant.dto;

import lombok.Data;

@Data
public class FundQueryDTO {
    // 搜索关键词（基金名称/简称/代码）
    private String keyword;

    // 基金类型（股票型/指数型/混合型/债券型）
    private String fundType;

    // 基金板块（光伏/半导体/纳斯达克）
    private String fundPlate;

    // 页码
    private Integer pageNum = 1;

    // 每页条数
    private Integer pageSize = 20;
}