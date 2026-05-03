package com.fund.assistant.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * 向基金分组中添加基金
 */
@Data
public class FavoriteAddDTO {
    @NotBlank(message = "基金代码不能为空")
    private String fundCode;
    
    private Long groupId; // 可选，不传则加入默认分组
}