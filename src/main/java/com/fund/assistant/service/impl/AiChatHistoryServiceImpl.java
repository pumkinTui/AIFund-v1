package com.fund.assistant.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fund.assistant.entity.AiChatHistory;
import com.fund.assistant.mapper.AiChatHistoryMapper;
import com.fund.assistant.service.AiChatHistoryService;
import com.fund.assistant.vo.AiChatSessionVO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiChatHistoryServiceImpl extends ServiceImpl<AiChatHistoryMapper, AiChatHistory> implements AiChatHistoryService {

    @Override
    public List<AiChatSessionVO> getSessions(Long userId) {
        return baseMapper.selectSessionsByUserId(userId);
    }

    @Override
    public int deleteSession(Long userId, String sessionId) {
        LambdaUpdateWrapper<AiChatHistory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AiChatHistory::getUserId, userId);
        wrapper.eq(AiChatHistory::getSessionId, sessionId);
        wrapper.set(AiChatHistory::getDelFlag, (byte) 1);
        return baseMapper.update(null, wrapper);
    }

    @Override
    public int renameSession(Long userId, String sessionId, String sessionName) {
        LambdaUpdateWrapper<AiChatHistory> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AiChatHistory::getUserId, userId);
        wrapper.eq(AiChatHistory::getSessionId, sessionId);
        wrapper.set(AiChatHistory::getSessionName, sessionName);
        return baseMapper.update(null, wrapper);
    }
}
