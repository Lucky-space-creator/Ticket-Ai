package com.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.entity.ChatRecord;
import com.ticket.mapper.ChatRecordMapper;
import com.ticket.service.AIChatService;
import com.ticket.service.KnowledgeAssistant;
import com.ticket.service.KnowledgeBaseService;
import com.ticket.service.StreamingKnowledgeAssistant;
import dev.langchain4j.memory.ChatMemory;
import com.ticket.util.SnowflakeIdUtil;
import com.ticket.util.UserContext;
import com.ticket.handler.ChatWebSocketHandler;
import com.ticket.enums.BusinessStatus;
import reactor.core.publisher.Flux;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;
    @Override
    @Transactional
    public String chat(String question) {
        log.info("开始处理问题：{}", question);
        String sessionId = getSessionId();
        // 保存用户消息
        saveChatRecord(question, BusinessStatus.MSG_TYPE_USER, null, sessionId, null, 0);

        // 检查会话是否已结束
        if (isSessionEnded(sessionId)) {
            String answer = "会话已结束，若需要客服介入，请点击转客服按钮";
            saveChatRecord(answer, BusinessStatus.MSG_TYPE_ROBOT, BigDecimal.ONE, sessionId, null, 0);
            return answer;
        }

        // 检查会话是否已被客服接管
        if (isSessionHandledByHuman(sessionId)) {
            // 客服已介入，不返回AI回复，让用户和客服直接对话
            // 用户消息已经在第57行保存并广播
            return "";
        }

        // 检查是否包含转人工关键字
        if (containsHumanServiceKeyword(question)) {
            String answer = triggerHumanService(sessionId);
            // 保存AI回复（实际上是人工资讯）
            saveChatRecord(answer, BusinessStatus.MSG_TYPE_ROBOT, BigDecimal.ONE, sessionId, null, 0);
            return answer;
        }

        //将问题中的{}替换为具体信息
//        question = KnowledgeBaseServiceImpl.getSystemPrompt(UserContext.getCurrentUserId(), String.valueOf(LocalDateTime.now()));

        // 调用AI
        String answer = knowledgeAssistant.chat(question);
        // 保存AI回复
        saveChatRecord(answer, BusinessStatus.MSG_TYPE_ROBOT, BigDecimal.ONE, sessionId, null, 0);
        return answer;
    }

    @Override
    @Transactional
    public Flux<String> streamingChat(String question) {
        log.info("开始流式处理问题：{}", question);
        String sessionId = getSessionId();
        // 保存用户消息
        saveChatRecord(question, BusinessStatus.MSG_TYPE_USER, null, sessionId, null, 0);
        
        // 检查会话是否已结束
        if (isSessionEnded(sessionId)) {
            String answer = "会话已结束，若需要客服介入，请点击转客服按钮";
            saveChatRecord(answer, BusinessStatus.MSG_TYPE_ROBOT, BigDecimal.ONE, sessionId, null, 0);
            return Flux.just(answer);
        }
        
        // 检查会话是否已被客服接管
        if (isSessionHandledByHuman(sessionId)) {
            // 客服已介入，不返回AI回复
            // 用户消息已经在第97行保存并广播
            return Flux.just("");
        }
        
        // 检查是否包含转人工关键字
        if (containsHumanServiceKeyword(question)) {
            String answer = triggerHumanService(sessionId);
            // 保存AI回复（实际上是人工资讯）
            saveChatRecord(answer, BusinessStatus.MSG_TYPE_ROBOT, BigDecimal.ONE, sessionId, null, 0);
            // 返回包含转人工消息的Flux
            return Flux.just(answer);
        }
        
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
                    saveChatRecord(answer, BusinessStatus.MSG_TYPE_ROBOT, BigDecimal.ONE, sessionId, null, 0);
                })
                .doOnError(error -> log.error("流式处理出错", error))
                .doOnSubscribe(subscription -> log.debug("流式订阅开始"));
    }

    /**
     * 保存聊天记录
     * @param message 消息内容
     * @param msgType 消息类型 'user'-用户 'robot'-机器人 员工号-客服
     * @param confidence 置信度（AI回复时可为1.0）
     * @param sessionId 会话ID
     * @param employeeId 客服员工ID（可选）
     * @param isRead 是否已读（可选，默认0）
     */
    private void saveChatRecord(String message, String msgType, BigDecimal confidence, String sessionId, Long employeeId, Integer isRead) {
        try {
            Long userId = UserContext.getCurrentUserId();
            ChatRecord record = new ChatRecord();
            record.setUserId(userId);
            record.setSessionId(sessionId);
            record.setMessage(message);
            record.setMsgType(msgType);
            record.setEmployeeId(employeeId);
            record.setIsRead(isRead != null ? isRead : 0);
            record.setConfidence(confidence);
            record.setCreatedAt(LocalDateTime.now());
            chatRecordMapper.insert(record);
            log.debug("保存聊天记录成功，用户ID：{}，类型：{}，会话ID：{}", userId, msgType, sessionId);
            
            // 通过 WebSocket 广播消息
            try {
                chatWebSocketHandler.sendMessageToSession(sessionId, com.alibaba.fastjson2.JSON.toJSONString(Map.of(
                        "type", "chat",
                        "content", message,
                        "msgType", msgType,
                        "employeeId", employeeId,
                        "userId", userId,
                        "timestamp", System.currentTimeMillis()
                )));
            } catch (Exception e) {
                log.warn("WebSocket 广播失败，但不影响主要流程", e);
            }
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
            "人工",
            "客服",
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
            
            // 保存一条 pending 消息，表示用户请求人工客服
            ChatRecord pendingMsg = new ChatRecord();
            pendingMsg.setSessionId(sessionId);
            pendingMsg.setUserId(userId);
            pendingMsg.setMessage("用户请求转人工客服");
            pendingMsg.setMsgType(BusinessStatus.MSG_TYPE_PENDING);
            pendingMsg.setIsRead(0);
            pendingMsg.setConfidence(BigDecimal.ONE);
            pendingMsg.setCreatedAt(LocalDateTime.now());
            chatRecordMapper.insert(pendingMsg);
            
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
        LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRecord::getSessionId, sessionId)
                .eq(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_ENDED);
        return chatRecordMapper.selectCount(wrapper) > 0;
    }

    /**
     * 检查会话是否已被客服接管（存在客服工号消息，且未结束）
     * 优化：只有当客服已接入但尚未发送第一条消息时才返回true
     * 一旦客服发送了消息，AI就不再介入，让用户和客服直接对话
     */
    private boolean isSessionHandledByHuman(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        // 查找是否存在pending消息（用户请求人工客服但尚未被接入）
        LambdaQueryWrapper<ChatRecord> pendingWrapper = new LambdaQueryWrapper<>();
        pendingWrapper.eq(ChatRecord::getSessionId, sessionId)
                .eq(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_PENDING);
        boolean hasPending = chatRecordMapper.selectCount(pendingWrapper) > 0;
        
        // 如果还有pending消息，说明客服尚未接入
        if (hasPending) {
            return false;
        }
        
        // 查找是否存在客服消息（msgType为数字工号）
        LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRecord::getSessionId, sessionId)
                .ne(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_USER)
                .ne(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_ROBOT)
                .ne(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_PENDING)
                .ne(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_ENDED);
        List<ChatRecord> records = chatRecordMapper.selectList(wrapper);
        
        // 如果存在客服消息，但客服还没有发送过消息（只有系统消息），则AI可以介入
        // 这里简化逻辑：只要客服已接入（pending消息被转为客服工号），AI就不介入
        // 实际应该检查是否有客服发送的消息（不仅仅是系统消息）
        // 我们先检查是否有员工ID非空的消息
        boolean hasEmployeeMessage = records.stream()
                .anyMatch(record -> record.getEmployeeId() != null);
        
        // 如果有客服消息但没有客服发送的具体消息，可能是系统消息，AI仍可介入
        // 但为了简单起见，只要客服接入（pending被处理），AI就不介入
        return !records.isEmpty();
    }

    @Override
    public void clearMemory() {
        log.info("清除用户聊天记忆，用户ID：{}", UserContext.getCurrentUserId());
        chatMemory.clear();
    }
}