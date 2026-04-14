package com.ticket.controller;

import com.ticket.dto.ChatRequest;
import com.ticket.dto.ChatResponse;
import com.ticket.service.AIChatService;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 智能客服控制器
 * 处理聊天相关请求，调用AI服务层
 */
@RestController
@RequestMapping("/api/chat")
public class AIChatController {

    @Resource
    private AIChatService aiChatService;  // 注入AI聊天服务

    @PostMapping("/ask")
    public ResponseUtil.Result<ChatResponse> ask(@Valid @RequestBody ChatRequest request) {
        String answer = aiChatService.chat(request.getQuestion());
        ChatResponse chatResponse = new ChatResponse(answer, true, System.currentTimeMillis());
        return ResponseUtil.success(chatResponse);
    }
}