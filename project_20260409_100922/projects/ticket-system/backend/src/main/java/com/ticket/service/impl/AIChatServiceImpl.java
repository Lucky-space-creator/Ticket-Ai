package com.ticket.service.impl;

import com.ticket.service.AIChatService;
import com.ticket.service.KnowledgeAssistant;
import com.ticket.service.StreamingKnowledgeAssistant;
import reactor.core.publisher.Flux;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 智能客服服务实现
 * 负责处理AI聊天业务逻辑，调用底层AI服务
 */
@Slf4j
@Service
public class AIChatServiceImpl implements AIChatService {

    @Resource
    private KnowledgeAssistant knowledgeAssistant;

    @Resource
    private StreamingKnowledgeAssistant streamingKnowledgeAssistant;

    @Override
    public String chat(String question) {
        log.info("开始处理问题：{}", question);
        return knowledgeAssistant.chat(question);
    }

    @Override
    public Flux<String> streamingChat(String question) {
        // 可以在这里添加业务逻辑，如日志记录、输入验证、缓存等
        // 后续微服务化时，可以将这部分逻辑独立为微服务
        log.info("开始流式处理问题：{}", question);
        Flux<String> flux = streamingKnowledgeAssistant.chat(question);
        // 添加日志记录，但不改变流
        return flux.doOnSubscribe(subscription -> log.debug("流式订阅开始"))
                .doOnNext(token -> log.trace("流式token：{}", token))
                .doOnComplete(() -> log.info("流式处理完成"))
                .doOnError(error -> log.error("流式处理出错", error));
    }
}