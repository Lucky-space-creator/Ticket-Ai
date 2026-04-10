package com.ticket.controller;

import com.ticket.dto.ChatRequest;
import com.ticket.dto.ChatResponse;
import com.ticket.service.KnowledgeAssistant;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Resource
    private KnowledgeAssistant knowledgeAssistant;  // 直接注入接口

    @PostMapping("/ask")
    public ChatResponse ask(@Valid @RequestBody ChatRequest request) {
        String answer = knowledgeAssistant.chat(request.getQuestion());
        return new ChatResponse(answer, true, System.currentTimeMillis());
    }
}