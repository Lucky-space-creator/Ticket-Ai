package com.ticket.aichat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.dto.mq.ChatRecordEvent;
import com.ticket.entity.ChatRecord;
import com.ticket.entity.ChatSession;
import com.ticket.aichat.mapper.ChatRecordMapper;
import com.ticket.aichat.service.AIChatService;
import com.ticket.service.KnowledgeAssistant;
import com.ticket.service.RocketMQProducerService;
import com.ticket.aichat.service.ChatSessionService;
import com.ticket.util.MQIdempotentUtil;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.Result;
import com.ticket.util.UserContext;
import com.ticket.util.TraceContext;
import com.ticket.util.AiChatStopWatch;
import com.ticket.util.TokenCountUtil;
import com.ticket.enums.BusinessStatus;
import dev.langchain4j.service.UserMessage;
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

/**
 * 智能客服服务实现
 * 负责处理AI聊天业务逻辑，调用底层AI服务
 */
@Service
public class AIChatServiceImpl implements AIChatService {

    private static final Logger log = LoggerFactory.getLogger(AIChatServiceImpl.class);

    private static final String HUMAN_SESSION_HINT = "当前正在与客服对话，请直接在聊天框中发送消息。";

    private static final String EMPTY_MODEL_FALLBACK = "抱歉，未获取到有效回答。请换一种问法或稍后重试；"
            + "若与查票、订单相关，请说明日期、出发到达站或订单号。";

    @Resource
    private KnowledgeAssistant knowledgeAssistant;

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
    public String chat(@UserMessage String question) {
        AiChatStopWatch stopWatch = new AiChatStopWatch("ai-chat-service").start();
        //设置traceId
        TraceContext.setTraceId(idempotentUtil.generateMessageId());
        log.info("[{}] 开始处理问题：{}", TraceContext.getTraceId(), question);

        Long userId = UserContext.getCurrentUserId();

        ChatSession humanServing = findHumanServingSession(userId);
        if (humanServing != null) {
            saveChatRecord(question, BusinessStatus.MSG_TYPE_USER, null, humanServing.getId(), null, 0, 0, 0);
            saveChatRecord(HUMAN_SESSION_HINT, BusinessStatus.MSG_TYPE_ROBOT, BigDecimal.ONE, humanServing.getId(), null, 0, 0, 0);
            stopWatch.stopAndLog();
            return HUMAN_SESSION_HINT;
        }

        // 检查是否包含转人工关键字
        if (containsHumanServiceKeyword(question)) {
            stopWatch.checkpoint("route_judge");
            String sessionId = chatSessionService.createSession(userId, "");
            String answer = triggerHumanService(sessionId);
            stopWatch.checkpoint("human_transfer");
            saveChatRecord(answer, BusinessStatus.MSG_TYPE_ROBOT, BigDecimal.ONE, sessionId, null, 0, 0, 0);
            stopWatch.stopAndLog();
            return answer;
        }

        String sessionId = resolveAiChatSessionId();
        if (isSessionEnded(sessionId)) {
            return "会话已结束，若需要客服介入，请点击转客服按钮";
        }
        saveChatRecord(question, BusinessStatus.MSG_TYPE_USER, null, sessionId, null, 0, 0, 0);
        stopWatch.checkpoint("db_save_user");

        // 调用AI（Result<String>获取TokenUsage）
        String answer;
        int inputTokens = 0;
        int outputTokens = 0;
        try {
            Result<String> result = knowledgeAssistant.chat(question);
            answer = result.content();
            if (answer == null || answer.isBlank()) {
                answer = EMPTY_MODEL_FALLBACK;
            }
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
        } catch (Exception e) {
            log.error("[{}] AI 调用异常: {}", TraceContext.getTraceId(), e.getMessage(), e);
            answer = EMPTY_MODEL_FALLBACK;
            outputTokens = TokenCountUtil.estimate(answer);
        }
        stopWatch.checkpoint("llm_call");
        saveChatRecord(answer, BusinessStatus.MSG_TYPE_ROBOT, BigDecimal.ONE, sessionId, null, 0, inputTokens, outputTokens);
        stopWatch.checkpoint("db_save_ai");
        stopWatch.stopAndLog();
        return answer;
    }

    /**
     * 兼容旧接口：内部走同步 {@link #chat(String)}，不再使用模型流式输出。
     */
    @Override
    public Flux<String> streamingChat(String question) {
        return Flux.just(chat(question));
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
     * 已接入人工客服的会话（ACTIVE 且已分配坐席），AI 通道仅返回提示，消息写入该会话。
     */
    private ChatSession findHumanServingSession(Long userId) {
        if (userId == null) {
            return null;
        }
        LambdaQueryWrapper<ChatSession> w = new LambdaQueryWrapper<>();
        w.eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getStatus, ChatSession.STATUS_ACTIVE)
                .isNotNull(ChatSession::getEmployeeId)
                .orderByDesc(ChatSession::getLastMessageAt)
                .last("LIMIT 1");
        return chatSessionService.getOne(w);
    }

    /**
     * AI 对话落库与 WS 广播使用 {@link ChatSession#STATUS_AI_ONLY} 会话，避免写入排队/人工会话导致客服端误收。
     */
    private String resolveAiChatSessionId() {
        Long userId = UserContext.getCurrentUserId();
        LambdaQueryWrapper<ChatSession> w = new LambdaQueryWrapper<>();
        w.eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getStatus, ChatSession.STATUS_AI_ONLY)
                .orderByDesc(ChatSession::getLastMessageAt)
                .last("LIMIT 1");
        ChatSession s = chatSessionService.getOne(w);
        if (s != null) {
            return s.getId();
        }
        return chatSessionService.createAiOnlySession(userId);
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

        // 仅显式转人工表述（避免「联系客服」、英文 human 等正常咨询误判）
        List<String> keywords = Arrays.asList(
                "转人工",
                "转人工客服",
                "人工客服",
                "转接人工",
                "人工坐席",
                "真人客服",
                "我要人工",
                "接人工"
        );
        for (String keyword : keywords) {
            if (question.contains(keyword)) {
                log.info("检测到转人工意图: {}，用户输入: {}", keyword, question);
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
                sessionId = chatSessionService.getOrCreateSession(userId);
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