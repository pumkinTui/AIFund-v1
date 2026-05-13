package com.fund.assistant.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI对话响应
 */
@Data
public class AiChatResponseDTO {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户提问内容
     */
    private String question;

    /**
     * AI回答内容
     */
    private String answer;

    /**
     * 是否包含可执行操作
     */
    private Boolean hasOperation = false;

    /**
     * 操作记录ID（当hasOperation=true时返回）
     */
    private Long operationId;

    /**
     * 操作类型：1=加仓 2=减仓 3=定投配置 4=发布帖子
     */
    private Byte operationType;

    /**
     * 操作内容摘要
     */
    private String operationSummary;

    /**
     * 回复时间
     */
    private LocalDateTime responseTime;
}