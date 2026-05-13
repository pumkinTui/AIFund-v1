package com.fund.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI对话请求DTO
 */
@Data
public class AiChatRequestDTO {

    /**
     * 会话ID（首次对话为空，后续对话携带）
     */
    private String sessionId;

    /**
     * 用户提问内容
     */
    @NotBlank(message = "提问内容不能为空")
    private String question;

    /**
     * 是否需要执行操作
     */
    private Boolean needExecute = false;

    // 是否来自图片识别流程（true时跳过历史持仓注入）
    private Boolean fromImage = false;

    // 前端展示用的提问文本（图片识别场景下）
    private String displayQuestion;

    // 用户上传的图片URL（存聊天记录用）
    private String imageUrl;
}