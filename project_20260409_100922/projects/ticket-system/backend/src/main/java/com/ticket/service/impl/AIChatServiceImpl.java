package com.ticket.service.impl;

import com.ticket.entity.ChatRecord;
import com.ticket.mapper.ChatRecordMapper;
import com.ticket.service.AIChatService;
import com.ticket.service.KnowledgeAssistant;
import com.ticket.service.KnowledgeBaseService;
import com.ticket.service.StreamingKnowledgeAssistant;
import dev.langchain4j.memory.ChatMemory;
import com.ticket.util.SnowflakeIdUtil;
import com.ticket.util.UserContext;
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
        saveChatRecord(question, BusinessStatus.MSG_TYPE_USER, null, sessionId, null, 0);

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

    @Override
    public void clearMemory() {
        log.info("清除用户聊天记忆，用户ID：{}", UserContext.getCurrentUserId());
        chatMemory.clear();
    }
}