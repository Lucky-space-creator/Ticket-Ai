package com.ticket.aichat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.dto.mq.ChatRecordEvent;
import com.ticket.entity.ChatRecord;
import com.ticket.entity.ChatSession;
import com.ticket.aichat.mapper.ChatRecordMapper;
import com.ticket.aichat.service.AIChatService;
import com.ticket.service.RocketMQProducerService;
import com.ticket.service.KnowledgeAssistant;
import com.ticket.service.StreamingKnowledgeAssistant;
import com.ticket.aichat.service.ChatSessionService;
import com.ticket.util.MQIdempotentUtil;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.Result;
import com.ticket.util.UserContext;
import com.ticket.util.TraceContext;
import com.ticket.util.TraceMdcHelper;
import com.ticket.util.AiChatStopWatch;
import com.ticket.util.TokenCountUtil;
import com.ticket.enums.BusinessStatus;
import reactor.core.publisher.Flux;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 智能客服服务实现
 * 负责处理AI聊天业务逻辑，调用底层AI服务
 */
@Service
public class AIChatServiceImpl implements AIChatService {

    private static final Logger log = LoggerFactory.getLogger(AIChatServiceImpl.class);

    @Resource
    private KnowledgeAssistant knowledgeAssistant;

    @Resource
    private StreamingKnowledgeAssistant streamingKnowledgeAssistant;

    @Resource
    private ChatRecordMapper chatRecordMapper;

    @Resource
    private ChatMemory chatMemory;

    @Resource
    private ChatSessionService chatSessionService;

    @Resource
    private RocketMQProducerService rocketMQProducerService;

    @Resource
    private MQIdempotentUtil idempotentUtil;
    @Override
    @Transactional
    public String chat(String question) {
        AiChatStopWatch stopWatch = new AiChatStopWatch("ai-chat-service").start();
        log.info("[{}] 开始处理问题：{}", TraceContext.getTraceId(), question);
        
        // 保存用户消息
        String sessionId = getSessionId();

        // 检查是否包含转人工关键字
        if (containsHumanServiceKeyword(question)) {
            stopWatch.checkpoint("route_judge");
            sessionId = chatSessionService.createSession(UserContext.getCurrentUserId(), "");
            String answer = triggerHumanService(sessionId);
            stopWatch.checkpoint("human_transfer");
            saveChatRecord(answer, BusinessStatus.MSG_TYPE_ROBOT, BigDecimal.ONE, sessionId, null, 0, 0, 0);
            stopWatch.stopAndLog();
            return answer;
        } else {
            saveChatRecord(question, BusinessStatus.MSG_TYPE_USER, null, sessionId, null, 0, 0, 0);
            stopWatch.checkpoint("db_save_user");
        }

        // 检查会话状态
        if (sessionId != null) {
            ChatSession session = chatSessionService.getById(sessionId);
            if (session != null && ChatSession.STATUS_ACTIVE.equals(session.getStatus())) {
                stopWatch.checkpoint("session_check");
                stopWatch.stopAndLog();
                return "";
            }
        }
        
        // 调用AI（Result<String>获取TokenUsage）
        String answer;
        int inputTokens = 0;
        int outputTokens = 0;
        try {
            Result<String> result = knowledgeAssistant.chat(question);
            answer = result.content();
            if (result.tokenUsage() != null) {
                inputTokens = result.tokenUsage().inputTokenCount();
                outputTokens = result.tokenUsage().outputTokenCount();
                log.info("[{}] Token消耗 - input:{}, output:{}",
                        TraceContext.getTraceId(), inputTokens, outputTokens);
            } else {
                // 回退：使用估算
                outputTokens = TokenCountUtil.estimate(answer);
                log.warn("[{}] 无法获取精确TokenUsage，使用估算值 output:{}",
                        TraceContext.getTraceId(), outputTokens);
            }
        } catch (IllegalStateException e) {
            log.error("[{}] RAG服务不可用，返回降级消息: {}",
                    TraceContext.getTraceId(), e.getMessage());
            answer = "抱歉，智能客服系统当前正在维护中，预计10分钟内恢复。"
                    + "您可以：1.查看【常见问题】页面 2.拨打客服热线12306";
            outputTokens = TokenCountUtil.estimate(answer);
        }
        stopWatch.checkpoint("llm_call");
        saveChatRecord(answer, BusinessStatus.MSG_TYPE_ROBOT, BigDecimal.ONE, sessionId, null, 0, inputTokens, outputTokens);
        stopWatch.checkpoint("db_save_ai");
        stopWatch.stopAndLog();
        return answer;
    }

    @Override
    @Transactional
    public Flux<String> streamingChat(String question) {
        AiChatStopWatch stopWatch = new AiChatStopWatch("ai-stream-service").start();
        log.info("[{}] 开始流式处理问题：{}", TraceContext.getTraceId(), question);
        
        String traceId = TraceContext.getTraceId();
        String sessionId = getSessionId();
        saveChatRecord(question, BusinessStatus.MSG_TYPE_USER, null, sessionId, null, 0, 0, 0);
        stopWatch.checkpoint("db_save_user");
        
        if (isSessionEnded(sessionId)) {
            String answer = "会话已结束，若需要客服介入，请点击转客服按钮";
            saveChatRecord(answer, BusinessStatus.MSG_TYPE_ROBOT, BigDecimal.ONE, sessionId, null, 0, 0, 0);
            return Flux.just(answer);
        }

        // 检查是否包含转人工关键字
        if (containsHumanServiceKeyword(question)) {
            stopWatch.checkpoint("route_judge");
            String answer = triggerHumanService(sessionId);
            saveChatRecord(answer, BusinessStatus.MSG_TYPE_ROBOT, BigDecimal.ONE, sessionId, null, 0, 0, 0);
            return Flux.just(answer);
        }
        
        // 检查会话状态
        if (sessionId != null) {
            ChatSession session = chatSessionService.getById(sessionId);
            if (session != null && ChatSession.STATUS_ACTIVE.equals(session.getStatus())) {
                String answer = "当前正在与客服对话，请直接在聊天框中发送消息。";
                saveChatRecord(answer, BusinessStatus.MSG_TYPE_ROBOT, BigDecimal.ONE, sessionId, null, 0, 0, 0);
                return Flux.just(answer);
            }
        }
        
        Flux<String> flux = streamingKnowledgeAssistant.chat(question);
        stopWatch.checkpoint("llm_stream_start");

        AtomicReference<StringBuilder> responseBuilder = new AtomicReference<>(new StringBuilder());
        
        return flux
                .doOnNext(token -> {
                    TraceMdcHelper.runWithTraceId(traceId, () -> {
                        log.trace("流式token：{}", token);
                    });
                    responseBuilder.get().append(token);
                })
                .doOnComplete(() -> {
                    stopWatch.checkpoint("llm_stream_complete");
                    log.info("[{}] 流式处理完成", TraceContext.getTraceId());
                    String answer = responseBuilder.get().toString();
                    // 流式场景：使用 TokenCountUtil 估算 token 数量
                    int inputTokens = TokenCountUtil.estimate(question);
                    int outputTokens = TokenCountUtil.estimate(answer);
                    log.info("[{}] Token消耗(估算) - input:{}, output:{}", 
                            TraceContext.getTraceId(), inputTokens, outputTokens);
                    saveChatRecord(answer, BusinessStatus.MSG_TYPE_ROBOT, BigDecimal.ONE, sessionId, null, 0, inputTokens, outputTokens);
                    stopWatch.checkpoint("db_save_ai");
                    stopWatch.stopAndLog();
                })
                .doOnError(error -> log.error("流式处理出错", error))
                .doOnSubscribe(subscription ->
                        TraceMdcHelper.runWithTraceId(traceId, () ->
                                log.debug("流式订阅开始")
                        )
                );
    }

    /**
     * 保存聊天记录（异步：通过RocketMQ发送，由消费者负责DB写入和WS广播）
     * @param message 消息内容
     * @param msgType 消息类型 'user'-用户 'robot'-机器人 员工号-客服
     * @param confidence 置信度（AI回复时可为1.0）
     * @param sessionId 会话ID (可填可不填)
     * @param employeeId 客服员工ID（可选）
     * @param isRead 是否已读（可选，默认0）
     * @param inputTokens AI输入Token数（用户消息为0）
     * @param outputTokens AI输出Token数（用户消息为0）
     */
    private void saveChatRecord(String message, String msgType, BigDecimal confidence,
                                String sessionId, Long employeeId, Integer isRead,
                                int inputTokens, int outputTokens) {
        try {
            ChatRecordEvent event = new ChatRecordEvent();
            event.setMessageId(idempotentUtil.generateMessageId());
            event.setUserId(UserContext.getCurrentUserId());
            event.setSessionId(sessionId);
            event.setMessage(message);
            event.setMsgType(msgType);
            event.setEmployeeId(employeeId);
            event.setConfidence(confidence);
            event.setInputTokens(inputTokens);
            event.setOutputTokens(outputTokens);
            event.setCreatedAt(LocalDateTime.now());
            event.setTraceId(TraceContext.getTraceId());
            rocketMQProducerService.sendChatRecordEvent(event);
            log.debug("已发送聊天记录到RocketMQ异步处理: userId={}, msgType={}", 
                    UserContext.getCurrentUserId(), msgType);
        } catch (Exception e) {
            log.error("发送聊天记录事件失败", e);
        }
    }

    /**
     * 获取会话ID
     */
    private String getSessionId() {
        //查询数据库中是否有未结束的会话且userid=当前用户，包括进行中和等待的
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, UserContext.getCurrentUserId())
                .ne(ChatSession::getStatus, ChatSession.STATUS_ENDED)
                .ne(ChatSession::getStatus, ChatSession.STATUS_AI_ONLY);
        //查询
        ChatSession session = chatSessionService.getOne(wrapper);

        return session != null ? session.getId() : null;

    }

    /**
     * 检测用户输入是否包含转人工关键字
     * @param question 用户输入
     * @return 是否包含转人工关键字
     */
    private boolean containsHumanServiceKeyword(String question) {
        if (question == null || question.trim().isEmpty()) {
            return false;
        }
        
        // 转人工关键字列表
        List<String> keywords = Arrays.asList(
            "转人工",
            "人工客服", 
            "人工服务",
            "转人工客服",
            "转接人工",
            "人工坐席",
            "真人客服",
            "联系客服",
            "找客服",
            "customer service",
            "human",
            "operator"
        );
        
        String lowerQuestion = question.toLowerCase();
        for (String keyword : keywords) {
            if (lowerQuestion.contains(keyword.toLowerCase())) {
                log.info("检测到转人工关键字: {}，用户输入: {}", keyword, question);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 触发转人工服务
     * @param sessionId 会话ID
     * @return 转人工结果消息
     */
    private String triggerHumanService(String sessionId) {
        try {
            Long userId = UserContext.getCurrentUserId();
            if (userId == null) {
                log.warn("用户未登录，无法转人工");
                return "请先登录后再请求人工客服";
            }

            if (sessionId == null) {
                sessionId = getSessionId();
            }

            // 异步保存 pending 消息（通过RocketMQ）
            ChatRecordEvent event = new ChatRecordEvent();
            event.setMessageId(idempotentUtil.generateMessageId());
            event.setUserId(userId);
            event.setSessionId(sessionId);
            event.setMessage("用户请求转人工客服");
            event.setMsgType(BusinessStatus.MSG_TYPE_PENDING);
            event.setConfidence(java.math.BigDecimal.ONE);
            event.setInputTokens(0);
            event.setOutputTokens(0);
            event.setCreatedAt(LocalDateTime.now());
            event.setTraceId(TraceContext.getTraceId());
            rocketMQProducerService.sendChatRecordEvent(event);

            log.info("用户 {} 触发转人工服务，会话ID: {}", userId, sessionId);
            return "已为您转接人工客服，请稍候，客服人员将很快为您服务。";
        } catch (Exception e) {
            log.error("触发转人工服务失败", e);
            return "转人工服务暂时不可用，请稍后再试或直接联系客服。";
        }
    }

    /**
     * 检查会话是否已结束
     */
    private boolean isSessionEnded(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        
        // 1. 检查会话表状态
        ChatSession session = chatSessionService.getById(sessionId);
        if (session != null && ChatSession.STATUS_ENDED.equals(session.getStatus())) {
            return true;
        }
        
        // 2. 回退到聊天记录检查（兼容旧逻辑）
        LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRecord::getSessionId, sessionId)
                .eq(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_ENDED);
        return chatRecordMapper.selectCount(wrapper) > 0;
    }



    @Override
    public void clearMemory() {
        log.info("清除用户聊天记忆，用户ID：{}", UserContext.getCurrentUserId());
        chatMemory.clear();
    }
}