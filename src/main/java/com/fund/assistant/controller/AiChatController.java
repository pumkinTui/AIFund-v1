package com.fund.assistant.controller;

import com.fund.assistant.dto.AiChatImageRequestDTO;
import com.fund.assistant.dto.AiChatRequestDTO;
import com.fund.assistant.dto.AiChatResponseDTO;
import com.fund.assistant.dto.AiConfirmDTO;
import com.fund.assistant.entity.AiChatHistory;
import com.fund.assistant.service.AiChatService;
import com.fund.assistant.util.OssService;
import com.fund.assistant.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI对话控制器 - 旺财助手
 */
@Slf4j
@RestController
@RequestMapping("/ai/chat")
public class AiChatController {

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private OssService ossService;

    /**
     * 上传图片到OSS（供AI图片识别使用）
     * 前端先调用此接口上传文件，拿到公网URL，再传给 /ai/chat/image
     */
    @PostMapping("/upload-image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("请选择要上传的图片");
        }
        log.info("用户上传图片：{}，大小：{}", file.getOriginalFilename(), file.getSize());
        String imageUrl = ossService.uploadImage(file);
        return Result.success(imageUrl, "图片上传成功");
    }

    /**
     * 发送消息给AI旺财
     */
    @PostMapping("/send")
    public Result<AiChatResponseDTO> sendMessage(@Validated @RequestBody AiChatRequestDTO request) {
        log.info("用户发起AI对话：{}", request.getQuestion());
        AiChatResponseDTO response = aiChatService.chat(request);
        return Result.success(response);
    }

    /**
     * 流式发送消息给AI旺财（SSE）
     */
    @PostMapping("/send/stream")
    public SseEmitter sendMessageStream(@RequestBody AiChatRequestDTO request) {
        log.info("用户发起流式AI对话：{}", request.getQuestion());
        SseEmitter emitter = new SseEmitter(60000L);
        aiChatService.chatStream(request, emitter);
        return emitter;
    }

    /**
     * 图片识别对话
     */
    @PostMapping("/image")
    public Result<AiChatResponseDTO> chatWithImage(@Validated @RequestBody AiChatImageRequestDTO request) {
        log.info("用户发起图片识别，图片URL：{}", request.getImageUrl());
        AiChatResponseDTO response = aiChatService.chatWithImage(request);
        return Result.success(response);
    }

    /**
     * 图片识别对话（流式SSE）
     */
    @PostMapping("/image/stream")
    public SseEmitter chatWithImageStream(@RequestBody AiChatImageRequestDTO request) {
        log.info("用户发起流式图片识别，图片URL：{}", request.getImageUrl());
        SseEmitter emitter = new SseEmitter(120000L);
        aiChatService.chatWithImageStream(request, emitter);
        return emitter;
    }

    /**
     * 查询对话历史记录
     * @param sessionId 会话ID（可选，不传则查询所有）
     */
    @GetMapping("/history")
    public Result<List<AiChatHistory>> getChatHistory(@RequestParam(required = false) String sessionId) {
        List<AiChatHistory> history = aiChatService.getHistory(sessionId);
        return Result.success(history);
    }

    /**
     * 删除单条对话记录
     */
    @PostMapping("/history/delete/{historyId}")
    public Result<Void> deleteChatHistory(@PathVariable Long historyId) {
        aiChatService.deleteHistory(historyId);
        return Result.success();
    }

    /**
     * 清空所有对话记录
     */
    @PostMapping("/history/clear")
    public Result<Void> clearChatHistory() {
        aiChatService.clearAllHistory();
        return Result.success();
    }

    /**
     * 确认执行AI建议的操作
     */
    @PostMapping("/confirm")
    public Result<Boolean> confirmOperation(@Validated @RequestBody AiConfirmDTO dto) {
        boolean result = aiChatService.confirmOperation(dto.getOperationId(), dto.getConfirm());
        String msg = dto.getConfirm() ? "操作已确认执行" : "操作已取消";
        return Result.success(result, msg);
    }
}