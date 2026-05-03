package com.fund.assistant.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 接收前端创建分组时传过来的参数
 */
@Data
public class GroupCreateDTO {

    @NotBlank(message = "分组名称不能为空")
    @Size(max = 20, message = "分组名称不能超过20")
    private String groupName;
}