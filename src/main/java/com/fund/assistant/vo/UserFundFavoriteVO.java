package com.fund.assistant.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的自选基金列表
 */
@Data
public class UserFundFavoriteVO {

    // 收藏记录ID
    private Long id;

    // 所属分组ID
    private Long groupId;

    // 分组名称
    private String groupName;

    // 基金代码
    private String fundCode;

    // 基金全称
    private String fundName;

    // 基金简称
    private String fundShortName;

    // 最新净值
    private BigDecimal latestNetValue;

    // 最新涨跌幅
    private BigDecimal latestChangeRate;

    // 收藏时间
    private LocalDateTime createTime;
}