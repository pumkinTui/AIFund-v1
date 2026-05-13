package com.fund.assistant.controller;

import com.fund.assistant.entity.UserFeedback;
import com.fund.assistant.service.UserFeedbackService;
import com.fund.assistant.util.Result;
import com.fund.assistant.util.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/user/feedback")
public class UserFeedbackController {

    @Autowired
    private UserFeedbackService userFeedbackService;

    @PostMapping("/submit")
    public Result<Void> submit(@RequestBody Map<String, String> body) {
        Long userId = UserContext.getUserId();
        String content = body.get("content");
        String contact = body.get("contact");
        if (content == null || content.trim().isEmpty()) return Result.error("建议内容不能为空");
        UserFeedback fb = new UserFeedback();
        fb.setUserId(userId);
        fb.setContent(content.trim());
        fb.setContact(contact);
        fb.setStatus((byte) 0);
        fb.setCreateTime(LocalDateTime.now());
        fb.setUpdateTime(LocalDateTime.now());
        userFeedbackService.save(fb);
        log.info("用户 {} 提交功能建议", userId);
        return Result.success();
    }
}
