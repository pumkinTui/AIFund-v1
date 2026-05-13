package com.fund.assistant.service;

import com.fund.assistant.dto.AiChatImageRequestDTO;
import com.fund.assistant.dto.AiChatRequestDTO;
import com.fund.assistant.dto.AiChatResponseDTO;
import com.fund.assistant.entity.AiChatHistory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface AiChatService {

    AiChatResponseDTO chat(AiChatRequestDTO request);

    void chatStream(AiChatRequestDTO request, SseEmitter emitter);

    List<AiChatHistory> getHistory(String sessionId);

    void deleteHistory(Long historyId);

    void clearAllHistory();

    boolean confirmOperation(Long operationId, boolean confirm);

    AiChatResponseDTO chatWithImage(AiChatImageRequestDTO request);

    void chatWithImageStream(AiChatImageRequestDTO request, SseEmitter emitter);
}
