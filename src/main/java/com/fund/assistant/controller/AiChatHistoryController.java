package com.fund.assistant.controller;

import com.fund.assistant.service.AiChatHistoryService;
import com.fund.assistant.util.Result;
import com.fund.assistant.util.UserContext;
import com.fund.assistant.vo.AiChatSessionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai/chat/sessions")
public class AiChatHistoryController {

    @Autowired
    private AiChatHistoryService aiChatHistoryService;

    @GetMapping
    public Result<List<AiChatSessionVO>> list() {
        Long userId = UserContext.getUserId();
        List<AiChatSessionVO> sessions = aiChatHistoryService.getSessions(userId);
        return Result.success(sessions);
    }

    @PostMapping("/create")
    public Result<String> create() {
        return Result.success(java.util.UUID.randomUUID().toString().replace("-", ""));
    }

    @PostMapping("/rename")
    public Result<Void> rename(@RequestParam String sessionId, @RequestParam String sessionName) {
        Long userId = UserContext.getUserId();
        aiChatHistoryService.renameSession(userId, sessionId, sessionName);
        return Result.success();
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam String sessionId) {
        Long userId = UserContext.getUserId();
        aiChatHistoryService.deleteSession(userId, sessionId);
        return Result.success();
    }
}
