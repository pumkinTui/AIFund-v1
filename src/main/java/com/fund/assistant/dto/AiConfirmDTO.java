package com.fund.assistant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI操作确认DTO
 */
@Data
public class AiConfirmDTO {

    /**
     * 操作记录ID
     */
    @NotNull(message = "操作记录ID不能为空")
    private Long operationId;

    /**
     * 是否确认执行：true=确认执行 false=取消
     */
    private Boolean confirm = true;
}