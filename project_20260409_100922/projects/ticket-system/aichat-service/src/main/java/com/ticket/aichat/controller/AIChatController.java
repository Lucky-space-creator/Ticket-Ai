package com.ticket.aichat.controller;

import com.ticket.dto.ChatRequest;
import com.ticket.dto.ChatResponse;
import com.ticket.aichat.service.AIChatService;
import com.ticket.aichat.service.KnowledgeBaseService;
import com.ticket.aichat.service.impl.KnowledgeBaseServiceImpl;
import com.ticket.util.*;
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
    public ResponseUtil.Result<ChatResponse> ask(@Valid @RequestBody ChatRequest request) {
        AiChatStopWatch stopWatch = new AiChatStopWatch("ai-chat-sync").start();
        String question = request.getQuestion();
        log.info("[{}] 用户提问：{}", TraceContext.getTraceId(), question);
        String answer = aiChatService.chat(question);
        stopWatch.checkpoint("controller_total");
        ChatResponse chatResponse = new ChatResponse(answer, true, System.currentTimeMillis());
        stopWatch.stopAndLog();
        return ResponseUtil.success(chatResponse);
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@Valid @RequestBody ChatRequest request) {
        AiChatStopWatch stopWatch = new AiChatStopWatch("ai-chat-stream").start();
        String question = request.getQuestion();
        log.info("[{}] 收到流式聊天请求，问题：{}", TraceContext.getTraceId(), question);

        SseEmitter emitter = new SseEmitter(60000L); // 60秒超时
        Flux<String> flux = aiChatService.streamingChat(question);

        log.info("[{}] 开始流式传输", TraceContext.getTraceId());
        String traceIdForReactor = TraceContext.getTraceId();
        flux.subscribe(
            token -> {
                try {
                    // Reactor子线程中恢复MDC
                    TraceMdcHelper.runWithTraceId(traceIdForReactor, () -> log.trace("发送token：{}", token));
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
                stopWatch.checkpoint("stream_complete");
                log.info("[{}] 流式传输完成", TraceContext.getTraceId());
                stopWatch.stopAndLog();
                emitter.complete();
            }
        );
        
        // 处理客户端断开连接
        emitter.onCompletion(() -> log.info("SSE连接完成"));
        emitter.onTimeout(() -> log.warn("SSE连接超时"));
        emitter.onError(error -> log.error("SSE连接错误", error));
        
        return emitter;
    }

    @PostMapping("/clear")
    public ResponseUtil.Result<Void> clearMemory() {
        log.info("清除当前用户聊天记忆");
        aiChatService.clearMemory();
        return ResponseUtil.success("清除成功");
    }
}