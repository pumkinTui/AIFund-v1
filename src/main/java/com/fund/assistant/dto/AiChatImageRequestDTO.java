package com.fund.assistant.dto;

import lombok.Data;

@Data
public class AiChatImageRequestDTO {

    private String sessionId;

    private String imageUrl;

    private String userPrompt;
}
