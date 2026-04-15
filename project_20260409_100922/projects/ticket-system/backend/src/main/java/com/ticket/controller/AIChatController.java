package com.ticket.controller;

import com.ticket.dto.ChatRequest;
import com.ticket.dto.ChatResponse;
import com.ticket.service.AIChatService;
import com.ticket.service.KnowledgeBaseService;
import com.ticket.service.impl.KnowledgeBaseServiceImpl;
import com.ticket.util.ResponseUtil;
import com.ticket.util.UserContext;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * 智能客服控制器
 * 处理聊天相关请求，调用AI服务层
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class AIChatController {

    private static final Logger log = LoggerFactory.getLogger(AIChatController.class);

    @Resource
    private AIChatService aiChatService;  // 注入AI聊天服务

    @PostMapping("/ask")
    public ResponseUtil.Result<ChatResponse> ask(@Valid @RequestBody ChatRequest request) throws FileNotFoundException {
        String systemPrompt = KnowledgeBaseServiceImpl.getSystemPrompt(UserContext.getCurrentUserId(), request.getQuestion());

        String answer = aiChatService.chat(systemPrompt);
        ChatResponse chatResponse = new ChatResponse(answer, true, System.currentTimeMillis());
        return ResponseUtil.success(chatResponse);
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@Valid @RequestBody ChatRequest request) throws FileNotFoundException {
        log.info("收到流式聊天请求，问题：{}", request.getQuestion());
        String systemPrompt = KnowledgeBaseServiceImpl.getSystemPrompt(UserContext.getCurrentUserId(), request.getQuestion());
        log.debug("系统提示：{}", systemPrompt);
        
        SseEmitter emitter = new SseEmitter(60000L); // 60秒超时
        Flux<String> flux = aiChatService.streamingChat(systemPrompt);
        
        log.info("开始流式传输");
        flux.subscribe(
            token -> {
                try {
                    log.trace("发送token：{}", token);
                    emitter.send(SseEmitter.event().data(token));
                } catch (IOException e) {
                    log.error("发送token时发生IO异常", e);
                    emitter.completeWithError(e);
                }
            },
            error -> {
                log.error("流式传输过程中发生错误", error);
                emitter.completeWithError(error);
            },
            () -> {
                log.info("流式传输完成");
                emitter.complete();
            }
        );
        
        // 处理客户端断开连接
        emitter.onCompletion(() -> log.info("SSE连接完成"));
        emitter.onTimeout(() -> log.warn("SSE连接超时"));
        emitter.onError(error -> log.error("SSE连接错误", error));
        
        return emitter;
    }
}