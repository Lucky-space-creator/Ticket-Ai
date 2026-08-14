package com.ticket.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.customer.client.AdminEmployeeInternalClient;
import com.ticket.customer.handler.ChatWebSocketHandler;
import com.ticket.customer.mapper.ChatRecordMapper;
import com.ticket.entity.ChatRecord;
import com.ticket.entity.ChatSession;
import com.ticket.enums.BusinessStatus;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 人工客服自动分配调度器：
 * 周期扫描处于 PENDING 状态、超过短暂等待阈值仍未接入的会话，自动分配给空闲坐席。
 * 采用定时扫表而非内存延时任务，进程重启后可自然恢复，避免会话永久滞留。
 */
@Slf4j
@Component
public class CustomerAutoAssignScheduler {

    private static final long ASSIGN_INTERVAL_MS = 5000;

    @Resource
    private ChatSessionService chatSessionService;

    @Resource
    private ChatRecordMapper chatRecordMapper;

    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;

    @Resource
    private AdminEmployeeInternalClient adminEmployeeInternalClient;

    @Scheduled(fixedDelay = ASSIGN_INTERVAL_MS)
    public void assignPendingSessions() {
        try {
            List<ChatSession> pendingSessions = chatSessionService.getPendingSessions();
            for (ChatSession session : pendingSessions) {
                assignOne(session);
            }
        } catch (Exception e) {
            log.warn("自动分配客服扫描失败", e);
        }
    }

    private void assignOne(ChatSession session) {
        String sessionId = session.getId();
        Long userId = session.getUserId();
        Long employeeId = adminEmployeeInternalClient.availableEmployeeId();
        if (employeeId == null) {
            degradeToAiOnly(sessionId, userId);
            return;
        }
        boolean accepted = chatSessionService.acceptSession(sessionId, employeeId);
        if (!accepted) {
            // 并发下已被其他分配流程接入，跳过
            return;
        }
        markPendingAsEmployee(sessionId, employeeId);
        sendSystemMessage(sessionId, userId, employeeId, "客服已介入，有什么可以帮助您的？");
        log.info("自动分配客服 {} 接入会话 {}", employeeId, sessionId);
    }

    private void markPendingAsEmployee(String sessionId, Long employeeId) {
        LambdaQueryWrapper<ChatRecord> pw = new LambdaQueryWrapper<>();
        pw.eq(ChatRecord::getSessionId, sessionId)
                .eq(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_PENDING);
        ChatRecord pending = chatRecordMapper.selectOne(pw);
        if (pending != null) {
            pending.setMsgType(BusinessStatus.MSG_TYPE_EMPLOYEE);
            pending.setEmployeeId(employeeId);
            pending.setIsRead(1);
            chatRecordMapper.updateById(pending);
        }
    }

    private void sendSystemMessage(String sessionId, Long userId, Long employeeId, String content) {
        ChatRecord systemMsg = new ChatRecord();
        systemMsg.setSessionId(sessionId);
        systemMsg.setUserId(userId);
        systemMsg.setMessage(content);
        systemMsg.setMsgType(BusinessStatus.MSG_TYPE_EMPLOYEE);
        systemMsg.setEmployeeId(employeeId);
        systemMsg.setIsRead(0);
        systemMsg.setConfidence(BigDecimal.ONE);
        systemMsg.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(systemMsg);
        chatSessionService.incrementMessageCount(sessionId);
        chatSessionService.updateLastMessageTime(sessionId);
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "chat");
            wsMessage.put("content", content);
            wsMessage.put("msgType", BusinessStatus.MSG_TYPE_EMPLOYEE);
            wsMessage.put("employeeId", employeeId);
            if (userId != null) {
                wsMessage.put("userId", userId);
            }
            wsMessage.put("timestamp", System.currentTimeMillis());
            chatWebSocketHandler.sendMessageToSession(sessionId, com.alibaba.fastjson2.JSON.toJSONString(wsMessage));
        } catch (Exception e) {
            log.warn("自动分配接入消息广播失败", e);
        }
    }

    private void degradeToAiOnly(String sessionId, Long userId) {
        ChatRecord busyMsg = new ChatRecord();
        busyMsg.setSessionId(sessionId);
        busyMsg.setUserId(userId);
        busyMsg.setMessage("当前客服忙，请稍后再试或继续使用AI助手。");
        busyMsg.setMsgType(BusinessStatus.MSG_TYPE_ROBOT);
        busyMsg.setIsRead(0);
        busyMsg.setConfidence(BigDecimal.ONE);
        busyMsg.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(busyMsg);
        chatSessionService.incrementMessageCount(sessionId);
        chatSessionService.updateLastMessageTime(sessionId);
        ChatSession sessionToUpdate = chatSessionService.getById(sessionId);
        if (sessionToUpdate != null) {
            sessionToUpdate.setStatus(ChatSession.STATUS_AI_ONLY);
            chatSessionService.updateById(sessionToUpdate);
        }
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "chat");
            wsMessage.put("content", "当前客服忙，请稍后再试或继续使用AI助手。");
            wsMessage.put("msgType", BusinessStatus.MSG_TYPE_ROBOT);
            if (userId != null) {
                wsMessage.put("userId", userId);
            }
            wsMessage.put("timestamp", System.currentTimeMillis());
            chatWebSocketHandler.sendMessageToSession(sessionId, com.alibaba.fastjson2.JSON.toJSONString(wsMessage));
        } catch (Exception e) {
            log.warn("客服忙降级消息广播失败", e);
        }
        log.info("客服忙，转人工失败，会话 {} 恢复AI对话", sessionId);
    }
}
