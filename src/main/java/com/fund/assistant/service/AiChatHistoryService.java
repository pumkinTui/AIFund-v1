package com.fund.assistant.service;

import com.fund.assistant.entity.AiChatHistory;
import com.fund.assistant.vo.AiChatSessionVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AiChatHistoryService extends IService<AiChatHistory> {

    List<AiChatSessionVO> getSessions(Long userId);

    int deleteSession(Long userId, String sessionId);

    int renameSession(Long userId, String sessionId, String sessionName);
}
