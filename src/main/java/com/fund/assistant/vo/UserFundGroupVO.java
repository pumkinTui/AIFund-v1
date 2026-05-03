package com.fund.assistant.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户的基金分组信息
 */
@Data
public class UserFundGroupVO {
    
    private Long id;            // 分组主键ID
    
    private String groupName;   // 分组名称（如：自选基金、稳健组合、长线持仓）
    
    private Integer sortOrder;  // 排序号（数字越小越靠前，用来控制分组展示顺序）
    
    private Integer favoriteCount; // 该分组下的基金数量
    
    private LocalDateTime createTime; // 创建时间（yyyy-MM-dd HH:mm:ss）
}