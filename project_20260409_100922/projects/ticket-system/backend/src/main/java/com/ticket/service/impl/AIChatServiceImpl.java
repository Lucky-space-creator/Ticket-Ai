package com.ticket.service.impl;

import com.ticket.entity.ChatRecord;
import com.ticket.mapper.ChatRecordMapper;
import com.ticket.service.AIChatService;
import com.ticket.service.KnowledgeAssistant;
import com.ticket.service.StreamingKnowledgeAssistant;
import dev.langchain4j.memory.ChatMemory;
import com.ticket.util.SnowflakeIdUtil;
import com.ticket.util.UserContext;
import reactor.core.publisher.Flux;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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

    @Resource
    private ChatRecordMapper chatRecordMapper;

    @Resource
    private ChatMemory chatMemory;
    @Override
    @Transactional
    public String chat(String question) {
        log.info("开始处理问题：{}", question);
        String sessionId = getSessionId();
        // 保存用户消息
        saveChatRecord(question, 1, null, sessionId);
        // 调用AI
        String answer = knowledgeAssistant.chat(question);
        // 保存AI回复
        saveChatRecord(answer, 2, BigDecimal.ONE, sessionId);
        return answer;
    }

    @Override
    @Transactional
    public Flux<String> streamingChat(String question) {
        log.info("开始流式处理问题：{}", question);
        String sessionId = getSessionId();
        // 保存用户消息
        saveChatRecord(question, 1, null, sessionId);
        
        Flux<String> flux = streamingKnowledgeAssistant.chat(question);
        // 用于累积完整响应
        AtomicReference<StringBuilder> responseBuilder = new AtomicReference<>(new StringBuilder());
        
        return flux
                .doOnNext(token -> {
                    log.trace("流式token：{}", token);
                    responseBuilder.get().append(token);
                })
                .doOnComplete(() -> {
                    log.info("流式处理完成");
                    String answer = responseBuilder.get().toString();
                    saveChatRecord(answer, 2, BigDecimal.ONE, sessionId);
                })
                .doOnError(error -> log.error("流式处理出错", error))
                .doOnSubscribe(subscription -> log.debug("流式订阅开始"));
    }

    /**
     * 保存聊天记录
     * @param message 消息内容
     * @param msgType 消息类型 1-用户 2-机器人
     * @param confidence 置信度（AI回复时可为1.0）
     * @param sessionId 会话ID
     */
    private void saveChatRecord(String message, Integer msgType, BigDecimal confidence, String sessionId) {
        try {
            Long userId = UserContext.getCurrentUserId();
            ChatRecord record = new ChatRecord();
            record.setUserId(userId);
            record.setSessionId(sessionId);
            record.setMessage(message);
            record.setMsgType(msgType);
            record.setConfidence(confidence);
            record.setCreatedAt(LocalDateTime.now());
            chatRecordMapper.insert(record);
            log.debug("保存聊天记录成功，用户ID：{}，类型：{}，会话ID：{}", userId, msgType, sessionId);
        } catch (Exception e) {
            log.error("保存聊天记录失败", e);
        }
    }

    /**
     * 获取会话ID，基于用户ID生成
     */
    private String getSessionId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId != null) {
            return "user_" + userId;
        }
        // 匿名用户不生成会话ID
        return null;
    }

    @Override
    public void clearMemory() {
        log.info("清除用户聊天记忆，用户ID：{}", UserContext.getCurrentUserId());
        chatMemory.clear();
    }
}