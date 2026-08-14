package com.ticket.aichat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.aichat.agent.FAQAgent;
import com.ticket.aichat.agent.HumanTransferAgent;
import com.ticket.aichat.agent.OrderAgent;
import com.ticket.aichat.agent.ProfileAgent;
import com.ticket.aichat.agent.TrainQueryAgent;
import com.ticket.dto.TokenUsage;
import com.ticket.dto.mq.ChatRecordEvent;
import com.ticket.entity.ChatRecord;
import com.ticket.entity.ChatSession;
import com.ticket.entity.KnowledgeBase;
import com.ticket.aichat.mapper.ChatRecordMapper;
import com.ticket.aichat.router.IntentRouter;
import com.ticket.aichat.router.IntentType;
import com.ticket.aichat.service.AIChatService;
import com.ticket.service.RocketMQProducerService;
import com.ticket.aichat.service.ChatSessionService;
import com.ticket.aichat.service.HumanTransferPublisher;
import com.ticket.aichat.service.QuickFaqMatchService;
import com.ticket.aichat.service.UserProfileService;
import com.ticket.util.MQIdempotentUtil;
import com.ticket.util.TokenMonitor;
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
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

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
    private ChatRecordMapper chatRecordMapper;

    @Resource
    private ChatMemory chatMemory;

    @Resource
    private ChatSessionService chatSessionService;

    @Resource
    private RocketMQProducerService rocketMQProducerService;

    @Resource
    private MQIdempotentUtil idempotentUtil;

    @Resource
    private HumanTransferPublisher humanTransferPublisher;

    @Resource
    private QuickFaqMatchService quickFaqMatchService;

    @Resource
    private UserProfileService userProfileService;

    // ==================== 多 Agent 路由 ====================

    @Resource
    private IntentRouter intentRouter;

    @Resource
    private TrainQueryAgent trainQueryAgent;

    @Resource
    private OrderAgent orderAgent;

    @Resource
    private FAQAgent faqAgent;

    @Resource
    private ProfileAgent profileAgent;

    @Resource
    private HumanTransferAgent humanTransferAgent;

    @Resource
    private TokenMonitor tokenMonitor;

    /**
     * 用户消息计数器（用于触发画像增量更新）
     */
    private final ConcurrentHashMap<Long, AtomicInteger> userMessageCounters = new ConcurrentHashMap<>();

    /**
     * 用户级聊天锁：保证同一用户的完整 ChatMemory 读写 + LLM 调用流程原子执行。
     * 防止同一用户并发请求导致 LangChain4j ChatMemory 出现消息丢失或错序。
     * key = userId，不同用户之间完全不阻塞。
     */
    private final ConcurrentHashMap<Long, ReentrantLock> userChatLocks = new ConcurrentHashMap<>();
    @Override
    @Transactional
    public String chat(@UserMessage String question) {
        AiChatStopWatch stopWatch = new AiChatStopWatch("ai-chat-service").start();
        if (!TraceContext.hasTraceId()) {
            TraceContext.setTraceId(idempotentUtil.generateMessageId());
        }
        String tid = TraceContext.getTraceId();
        if (tid != null) {
            MDC.put(TraceContext.TRACE_ID_KEY, tid);
        }
        log.info("[{}] 开始处理问题：{}", tid, question);

        Long userId = UserContext.getCurrentUserId();

        // 已接入人工客服时，AI 通道仅返回提示
        ChatSession humanServing = findHumanServingSession(userId);
        if (humanServing != null) {
            saveChatRecord(question, BusinessStatus.MSG_TYPE_USER,
                    null, humanServing.getId(), null, 0, 0, 0);
            saveChatRecord(HUMAN_SESSION_HINT, BusinessStatus.MSG_TYPE_ROBOT,
                    BigDecimal.ONE, humanServing.getId(), null, 0, 0, 0);
            stopWatch.stopAndLog();
            return HUMAN_SESSION_HINT;
        }

        // 已发起转人工但坐席尚未接入（pending）：避免 AI 误接管，提示等待客服
        ChatSession pendingHuman = findPendingHumanSession(userId);
        if (pendingHuman != null) {
            saveChatRecord(question, BusinessStatus.MSG_TYPE_USER,
                    null, pendingHuman.getId(), null, 0, 0, 0);
            String waitingHint = "您的人工客服请求已提交，正在为您接入，请稍候……";
            saveChatRecord(waitingHint, BusinessStatus.MSG_TYPE_ROBOT,
                    BigDecimal.ONE, pendingHuman.getId(), null, 0, 0, 0);
            stopWatch.stopAndLog();
            return waitingHint;
        }

        // 意图分类：规则优先 → LLM 兜底
        IntentType intent = intentRouter.classify(question);
        stopWatch.checkpoint("intent_classify");
        log.info("[{}] 意图分类结果: {}", tid, intent);

        // 转人工：独立流程，不经过 FAQ 和 Agent
        if (intent == IntentType.HUMAN_TRANSFER) {
            String sessionId = chatSessionService.createPendingSession(userId);
            saveChatRecord(question, BusinessStatus.MSG_TYPE_USER,
                    null, sessionId, null, 0, 0, 0);
            String answer = humanTransferPublisher.publish(userId, sessionId, null);
            stopWatch.checkpoint("human_transfer");
            saveChatRecord(answer, BusinessStatus.MSG_TYPE_ROBOT,
                    BigDecimal.ONE, sessionId, null, 0, 0, 0);
            stopWatch.stopAndLog();
            return answer;
        }

        // 打招呼：硬编码回复，不消耗 Token
        if (intent == IntentType.GREETING) {
            String sessionId = chatSessionService.getOrCreateAiOnlySessionId(userId);
            saveChatRecord(question, BusinessStatus.MSG_TYPE_USER,
                    null, sessionId, null, 0, 0, 0);
            String answer = "您好！我是智能客服助手，可以帮您：\n"
                    + "1. 查询车次和票价\n"
                    + "2. 引导购票、退票、支付\n"
                    + "3. 回答铁路相关知识问题\n"
                    + "4. 管理个人信息和联系人\n"
                    + "请问有什么可以帮您？";
            saveChatRecord(answer, BusinessStatus.MSG_TYPE_ROBOT,
                    BigDecimal.ONE, sessionId, null, 0, 0, 0);
            stopWatch.stopAndLog();
            return answer;
        }

        // 常规流程：获取/创建 AI 会话
        String sessionId = chatSessionService.getOrCreateAiOnlySessionId(userId);
        if (isSessionEnded(sessionId)) {
            return "会话已结束，若需要客服介入，请点击转客服按钮";
        }
        saveChatRecord(question, BusinessStatus.MSG_TYPE_USER,
                null, sessionId, null, 0, 0, 0);
        stopWatch.checkpoint("db_save_user");

        // FAQ 直通命中：跳过所有 Agent
        if (intent == IntentType.KNOWLEDGE) {
            Optional<KnowledgeBase> faqHit = quickFaqMatchService.tryHit(question);
            if (faqHit.isPresent()) {
                String ans = faqHit.get().getAnswer();
                int estimated = TokenCountUtil.estimate(ans);
                log.info("[{}] FAQ 直通命中，跳过 LLM", tid);
                tokenMonitor.record(TokenUsage.estimated(estimated, "faq_bypass"), userId);
                saveChatRecord(ans, BusinessStatus.MSG_TYPE_ROBOT,
                        BigDecimal.ONE, sessionId, null, 0, 0, estimated);
                stopWatch.stopAndLog();
                return ans;
            }
        }

        // 调用对应 Agent（用户级锁保证 ChatMemory 并发安全）
        String answer;
        int inputTokens = 0;
        int outputTokens = 0;
        ReentrantLock chatLock = userChatLocks.computeIfAbsent(
                userId, k -> new ReentrantLock());
        chatLock.lock();
        try {
            AgentCallResult callResult = dispatchToAgent(intent, question);
            answer = callResult.answer;
            inputTokens = callResult.inputTokens;
            outputTokens = callResult.outputTokens;
        } catch (IllegalStateException e) {
            log.error("[{}] Agent 服务不可用: {}", tid, e.getMessage(), e);
            answer = "抱歉，智能客服系统当前正在维护中，预计10分钟内恢复。"
                    + "您可以：1.查看【常见问题】页面 2.拨打客服热线12306";
            outputTokens = TokenCountUtil.estimate(answer);
            tokenMonitor.record(
                    tokenMonitor.estimateOutput(answer, "fallback"), userId);
        } catch (Exception e) {
            log.error("[{}] Agent 调用异常: {}", tid, e.getMessage(), e);
            answer = EMPTY_MODEL_FALLBACK;
            outputTokens = TokenCountUtil.estimate(answer);
            tokenMonitor.record(
                    tokenMonitor.estimateOutput(answer, "error"), userId);
        } finally {
            chatLock.unlock();
        }
        stopWatch.checkpoint("agent_call");

        saveChatRecord(answer, BusinessStatus.MSG_TYPE_ROBOT,
                BigDecimal.ONE, sessionId, null, 0, inputTokens, outputTokens);
        stopWatch.checkpoint("db_save_ai");

        // 消息计数：达到阈值时异步触发画像增量更新
        checkAndTriggerProfileUpdate(userId, sessionId);

        stopWatch.stopAndLog();
        return answer;
    }

    /**
     * 根据意图分派到对应的 Specialist Agent。
     *
     * @param intent   意图类型
     * @param question 用户问题
     * @return Agent 调用结果（含 Token 统计）
     */
    private AgentCallResult dispatchToAgent(IntentType intent, String question) {
        Long userId = UserContext.getCurrentUserId();
        String source;

        // KNOWLEDGE 意图走 FAQAgent（Result<String> 返回 TokenUsage）
        if (intent == IntentType.KNOWLEDGE) {
            source = "agent:FAQAgent";
            Result<String> result = faqAgent.chat(question);
            String answer = result.content();
            if (answer == null || answer.isBlank()) {
                answer = EMPTY_MODEL_FALLBACK;
            }
            // 从 Result 提取精确 TokenUsage
            if (result.tokenUsage() != null) {
                TokenUsage usage = tokenMonitor.extractPrecise(
                        result.tokenUsage().inputTokenCount(),
                        result.tokenUsage().outputTokenCount(),
                        source);
                if (usage != null) {
                    tokenMonitor.record(usage, userId);
                    return new AgentCallResult(answer,
                            usage.inputTokens(), usage.outputTokens());
                }
            }
            // 无精确 TokenUsage 时估算
            TokenUsage estimated = tokenMonitor.estimateOutput(answer, source);
            tokenMonitor.record(estimated, userId);
            return new AgentCallResult(answer, 0, estimated.outputTokens());
        }

        // 其他 Agent 返回 String（无精确 TokenUsage，需估算）
        String agentAnswer;
        switch (intent) {
            case TRAIN_QUERY:
                source = "agent:TrainQueryAgent";
                agentAnswer = trainQueryAgent.chat(question);
                break;
            case ORDER:
                source = "agent:OrderAgent";
                agentAnswer = orderAgent.chat(question);
                break;
            case PROFILE:
                source = "agent:ProfileAgent";
                agentAnswer = profileAgent.chat(question);
                break;
            case HUMAN_TRANSFER:
                // 不应到达此处（已在上层处理），防御性兜底
                source = "agent:HumanTransferAgent";
                agentAnswer = humanTransferAgent.chat(question);
                break;
            default:
                source = "agent:TrainQueryAgent";
                agentAnswer = trainQueryAgent.chat(question);
                break;
        }

        if (agentAnswer == null || agentAnswer.isBlank()) {
            agentAnswer = EMPTY_MODEL_FALLBACK;
        }
        int estimatedOutput = TokenCountUtil.estimate(agentAnswer);
        tokenMonitor.record(
                tokenMonitor.estimateOutput(agentAnswer, source), userId);
        return new AgentCallResult(agentAnswer, 0, estimatedOutput);
    }

    /**
     * Agent 调用结果（内部值对象）
     */
    private record AgentCallResult(String answer, int inputTokens, int outputTokens) {
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
     * 查找用户已发起但尚未接入的人工会话（pending 状态）。
     * 用于转人工真空期：用户提交转人工后、坐席接入前，AI 不应接管该会话。
     */
    private ChatSession findPendingHumanSession(Long userId) {
        if (userId == null) {
            return null;
        }
        LambdaQueryWrapper<ChatSession> w = new LambdaQueryWrapper<>();
        w.eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getStatus, ChatSession.STATUS_PENDING)
                .orderByDesc(ChatSession::getLastMessageAt)
                .last("LIMIT 1");
        return chatSessionService.getOne(w);
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

    /**
     * 消息计数达到阈值时，异步触发用户画像增量更新。
     * 使用 compareAndSet 保证只有一个线程能成功触发，避免重复生成。
     */
    private void checkAndTriggerProfileUpdate(Long userId, String sessionId) {
        if (userId == null || !(userProfileService instanceof UserProfileServiceImpl impl)) {
            return;
        }
        AtomicInteger counter = userMessageCounters.computeIfAbsent(userId, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();
        int threshold = impl.getMessageThreshold();
        if (count >= threshold) {
            // CAS：将计数器回退 threshold（兼容并发累加越过阈值的场景），
            // 只有一个线程能成功触发画像更新，避免重复生成且不会卡死在越过阈值的计数上。
            if (counter.compareAndSet(count, count - threshold)) {
                log.info("用户消息达到阈值，触发画像增量更新: userId={}, threshold={}", userId, threshold);
                userProfileService.generateProfile(userId, sessionId);
            }
        }
    }
}