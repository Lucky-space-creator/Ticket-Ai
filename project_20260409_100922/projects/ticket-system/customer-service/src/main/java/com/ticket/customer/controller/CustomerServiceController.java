package com.ticket.customer.controller;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.customer.handler.ChatWebSocketHandler;
import com.ticket.customer.mapper.ChatRecordMapper;
import com.ticket.customer.mapper.ChatSessionMapper;
import com.ticket.customer.service.ChatSessionService;
import com.ticket.entity.ChatRecord;
import com.ticket.entity.ChatSession;
import com.ticket.enums.BusinessStatus;
import com.ticket.enums.ResponseCode;
import com.ticket.util.ResponseUtil;
import com.ticket.util.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/customer-service")
public class CustomerServiceController {

    @Resource
    private ChatRecordMapper chatRecordMapper;

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Resource
    private ChatSessionService chatSessionService;

    @Resource
    private ChatWebSocketHandler chatWebSocketHandler;

    @GetMapping("/pending-sessions")
    public ResponseUtil.Result<?> getPendingSessions() {
        Long employeeId = UserContext.getCurrentEmployeeId();
        if (employeeId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }
        List<ChatSession> pendingSessions = chatSessionService.getPendingSessions();
        List<Map<String, Object>> sessions = pendingSessions.stream().map(session -> {
            LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatRecord::getSessionId, session.getId())
                    .eq(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_PENDING)
                    .orderByDesc(ChatRecord::getCreatedAt)
                    .last("LIMIT 1");
            ChatRecord lastRecord = chatRecordMapper.selectOne(wrapper);
            Map<String, Object> map = new HashMap<>();
            map.put("sessionId", session.getId());
            map.put("userId", session.getUserId());
            if (lastRecord != null) {
                map.put("lastMessage", lastRecord.getMessage());
                map.put("lastMessageTime", lastRecord.getCreatedAt());
            } else {
                map.put("lastMessage", "");
                map.put("lastMessageTime", session.getLastMessageAt());
            }
            map.put("unreadCount", 0);
            return map;
        }).collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("sessions", sessions);
        return ResponseUtil.success(result);
    }

    @GetMapping("/serving-sessions")
    public ResponseUtil.Result<?> getServingSessions() {
        Long employeeId = UserContext.getCurrentEmployeeId();
        if (employeeId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getStatus, ChatSession.STATUS_ACTIVE)
                .eq(ChatSession::getEmployeeId, employeeId)
                .orderByDesc(ChatSession::getLastMessageAt);
        List<ChatSession> activeSessions = chatSessionMapper.selectList(wrapper);
        List<Map<String, Object>> sessions = activeSessions.stream().map(session -> {
            LambdaQueryWrapper<ChatRecord> lastMsgWrapper = new LambdaQueryWrapper<>();
            lastMsgWrapper.eq(ChatRecord::getSessionId, session.getId())
                    .orderByDesc(ChatRecord::getCreatedAt)
                    .last("LIMIT 1");
            ChatRecord lastRecord = chatRecordMapper.selectOne(lastMsgWrapper);
            LambdaQueryWrapper<ChatRecord> unreadWrapper = new LambdaQueryWrapper<>();
            unreadWrapper.eq(ChatRecord::getSessionId, session.getId())
                    .eq(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_USER)
                    .eq(ChatRecord::getIsRead, 0);
            int unreadCount = chatRecordMapper.selectCount(unreadWrapper).intValue();
            Map<String, Object> map = new HashMap<>();
            map.put("sessionId", session.getId());
            map.put("userId", session.getUserId());
            if (lastRecord != null) {
                map.put("lastMessage", lastRecord.getMessage());
                map.put("lastMessageTime", lastRecord.getCreatedAt());
            } else {
                map.put("lastMessage", "");
                map.put("lastMessageTime", session.getLastMessageAt());
            }
            map.put("unreadCount", unreadCount);
            return map;
        }).collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("sessions", sessions);
        return ResponseUtil.success(result);
    }

    @GetMapping("/ended-sessions")
    public ResponseUtil.Result<?> getEndedSessions() {
        Long employeeId = UserContext.getCurrentEmployeeId();
        if (employeeId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getEmployeeId, employeeId)
                .eq(ChatSession::getStatus, ChatSession.STATUS_ENDED)
                .orderByDesc(ChatSession::getLastMessageAt);
        List<ChatSession> endedSessions = chatSessionMapper.selectList(wrapper);
        List<Map<String, Object>> sessions = endedSessions.stream().map(session -> {
            LambdaQueryWrapper<ChatRecord> lastMsgWrapper = new LambdaQueryWrapper<>();
            lastMsgWrapper.eq(ChatRecord::getSessionId, session.getId())
                    .orderByDesc(ChatRecord::getCreatedAt)
                    .last("LIMIT 1");
            ChatRecord lastRecord = chatRecordMapper.selectOne(lastMsgWrapper);
            Map<String, Object> map = new HashMap<>();
            map.put("sessionId", session.getId());
            map.put("userId", session.getUserId());
            if (lastRecord != null) {
                map.put("lastMessage", lastRecord.getMessage());
                map.put("lastMessageTime", lastRecord.getCreatedAt());
            } else {
                map.put("lastMessage", "");
                map.put("lastMessageTime", session.getLastMessageAt());
            }
            map.put("unreadCount", 0);
            return map;
        }).collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("sessions", sessions);
        return ResponseUtil.success(result);
    }

    @PostMapping("/accept-session")
    public ResponseUtil.Result<?> acceptSession(@RequestBody Map<String, String> params) {
        Long employeeId = UserContext.getCurrentEmployeeId();
        if (employeeId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }
        String sessionId = params.get("sessionId");
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return ResponseUtil.error(ResponseCode.PARAM_ERROR);
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null && ChatSession.STATUS_ENDED.equals(session.getStatus())) {
            return ResponseUtil.error("会话已结束，无法再次接入");
        }
        if (session == null) {
            session = new ChatSession();
            session.setId(sessionId);
            session.setUserId(null);
            session.setStatus(ChatSession.STATUS_ACTIVE);
            session.setEmployeeId(employeeId);
            session.setMessageCount(0);
            session.setLastMessageAt(LocalDateTime.now());
            chatSessionMapper.insert(session);
        } else {
            session.setStatus(ChatSession.STATUS_ACTIVE);
            session.setEmployeeId(employeeId);
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }
        LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRecord::getSessionId, sessionId)
                .eq(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_PENDING);
        ChatRecord pendingRecord = chatRecordMapper.selectOne(wrapper);
        if (pendingRecord != null) {
            pendingRecord.setMsgType(BusinessStatus.MSG_TYPE_EMPLOYEE);
            pendingRecord.setEmployeeId(employeeId);
            pendingRecord.setIsRead(1);
            chatRecordMapper.updateById(pendingRecord);
        }
        ChatRecord systemMsg = new ChatRecord();
        systemMsg.setSessionId(sessionId);
        systemMsg.setUserId(pendingRecord != null ? pendingRecord.getUserId() : null);
        systemMsg.setMessage("客服已介入，有什么可以帮助您的？");
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
            wsMessage.put("content", "客服已介入，有什么可以帮助您的？");
            wsMessage.put("msgType", BusinessStatus.MSG_TYPE_EMPLOYEE);
            wsMessage.put("employeeId", employeeId);
            Long userIdValue = pendingRecord != null ? pendingRecord.getUserId() : null;
            if (userIdValue != null) {
                wsMessage.put("userId", userIdValue);
            }
            wsMessage.put("timestamp", System.currentTimeMillis());
            chatWebSocketHandler.sendMessageToSession(sessionId, JSON.toJSONString(wsMessage));
        } catch (Exception e) {
            log.warn("WebSocket 广播失败，但不影响主要流程", e);
        }
        log.info("客服 {} 接入会话 {}", employeeId, sessionId);
        return ResponseUtil.success("会话接入成功");
    }

    @PostMapping("/end-session")
    public ResponseUtil.Result<?> endSession(@RequestBody Map<String, String> params) {
        Long employeeId = UserContext.getCurrentEmployeeId();
        if (employeeId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }
        String sessionId = params.get("sessionId");
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return ResponseUtil.error(ResponseCode.PARAM_ERROR);
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null && ChatSession.STATUS_ENDED.equals(session.getStatus())) {
            return ResponseUtil.success("会话已结束");
        }
        if (session == null) {
            session = new ChatSession();
            session.setId(sessionId);
            session.setEmployeeId(employeeId);
            session.setStatus(ChatSession.STATUS_ENDED);
            session.setMessageCount(0);
            session.setLastMessageAt(LocalDateTime.now());
            chatSessionMapper.insert(session);
        } else {
            session.setStatus(ChatSession.STATUS_ENDED);
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }
        ChatRecord endMsg = new ChatRecord();
        endMsg.setSessionId(sessionId);
        endMsg.setMessage("客服会话已结束，若还需要客服接入，请点击转客服按钮");
        endMsg.setMsgType(BusinessStatus.MSG_TYPE_ENDED);
        endMsg.setEmployeeId(employeeId);
        endMsg.setIsRead(0);
        endMsg.setConfidence(BigDecimal.ONE);
        endMsg.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(endMsg);
        chatSessionService.incrementMessageCount(sessionId);
        chatSessionService.updateLastMessageTime(sessionId);
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "chat");
            wsMessage.put("content", "客服会话已结束，若还需要客服接入，请点击转客服按钮");
            wsMessage.put("msgType", BusinessStatus.MSG_TYPE_ENDED);
            wsMessage.put("employeeId", employeeId);
            wsMessage.put("timestamp", System.currentTimeMillis());
            chatWebSocketHandler.sendMessageToSession(sessionId, JSON.toJSONString(wsMessage));
        } catch (Exception e) {
            log.warn("WebSocket 广播失败，但不影响主要流程", e);
        }
        log.info("客服 {} 结束会话 {}", employeeId, sessionId);
        return ResponseUtil.success("会话结束成功");
    }

    @GetMapping("/history")
    public ResponseUtil.Result<?> getHistory(@RequestParam String sessionId) {
        Long employeeId = UserContext.getCurrentEmployeeId();
        if (employeeId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }
        LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRecord::getSessionId, sessionId)
                .orderByAsc(ChatRecord::getCreatedAt);
        List<ChatRecord> records = chatRecordMapper.selectList(wrapper);
        for (ChatRecord record : records) {
            if (BusinessStatus.MSG_TYPE_USER.equals(record.getMsgType()) && record.getIsRead() == 0) {
                record.setIsRead(1);
                chatRecordMapper.updateById(record);
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("messages", records);
        return ResponseUtil.success(result);
    }

    @PostMapping(value = "/request-human", consumes = "application/json")
    public ResponseUtil.Result<?> requestHumanService(@RequestBody Map<String, String> params) {
        String sessionId = params.get("sessionId");
        String reason = params.get("reason");
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = chatSessionService.getOrCreateSession(userId);
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            session = new ChatSession();
            session.setId(sessionId);
            session.setUserId(userId);
            session.setStatus(ChatSession.STATUS_PENDING);
            session.setTitle("用户请求转人工客服");
            session.setMessageCount(0);
            session.setLastMessageAt(LocalDateTime.now());
            chatSessionMapper.insert(session);
        } else if (!ChatSession.STATUS_PENDING.equals(session.getStatus())) {
            session.setStatus(ChatSession.STATUS_PENDING);
            session.setTitle("用户请求转人工客服");
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }
        String message = "用户请求转人工客服";
        if (reason != null && !reason.trim().isEmpty()) {
            message += "，原因：" + reason.trim();
        }
        ChatRecord pendingMsg = new ChatRecord();
        pendingMsg.setSessionId(sessionId);
        pendingMsg.setUserId(userId);
        pendingMsg.setMessage(message);
        pendingMsg.setMsgType(BusinessStatus.MSG_TYPE_PENDING);
        pendingMsg.setIsRead(0);
        pendingMsg.setConfidence(BigDecimal.ONE);
        pendingMsg.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(pendingMsg);
        chatSessionService.incrementMessageCount(sessionId);
        chatSessionService.updateLastMessageTime(sessionId);
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "notification");
            wsMessage.put("notificationType", "new_pending_session");
            wsMessage.put("sessionId", sessionId);
            wsMessage.put("userId", userId);
            wsMessage.put("content", message);
            wsMessage.put("timestamp", System.currentTimeMillis());
            chatWebSocketHandler.sendMessageToGlobal(JSON.toJSONString(wsMessage));
        } catch (Exception e) {
            log.warn("发送全局通知失败，但不影响主要流程", e);
        }
        // 自动分配交由 CustomerAutoAssignScheduler 周期扫描 PENDING 会话处理，
        // 避免内存定时任务在进程重启后丢失导致会话永久滞留。
        log.info("用户 {} 请求转人工客服，会话ID: {}，原因: {}", userId, sessionId, reason);
        return ResponseUtil.success("转人工请求已提交");
    }

    @GetMapping("/test")
    public ResponseUtil.Result<?> testEndpoint() {
        return ResponseUtil.success("客服API工作正常");
    }

    @PostMapping("/user-end-session")
    public ResponseUtil.Result<?> userEndSession(@RequestBody Map<String, String> params) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }
        String sessionId = params.get("sessionId");
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = chatSessionService.getOrCreateSession(userId);
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null && ChatSession.STATUS_ENDED.equals(session.getStatus())) {
            return ResponseUtil.success("会话已结束");
        }
        if (session == null) {
            session = new ChatSession();
            session.setId(sessionId);
            session.setUserId(userId);
            session.setStatus(ChatSession.STATUS_ENDED);
            session.setMessageCount(0);
            session.setLastMessageAt(LocalDateTime.now());
            chatSessionMapper.insert(session);
        } else {
            session.setStatus(ChatSession.STATUS_ENDED);
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }
        ChatRecord endMsg = new ChatRecord();
        endMsg.setSessionId(sessionId);
        endMsg.setUserId(userId);
        endMsg.setMessage("用户已结束本次会话");
        endMsg.setMsgType(BusinessStatus.MSG_TYPE_ENDED);
        endMsg.setIsRead(0);
        endMsg.setConfidence(BigDecimal.ONE);
        endMsg.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(endMsg);
        chatSessionService.incrementMessageCount(sessionId);
        chatSessionService.updateLastMessageTime(sessionId);
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "chat");
            wsMessage.put("content", "用户已结束本次会话");
            wsMessage.put("msgType", BusinessStatus.MSG_TYPE_ENDED);
            wsMessage.put("userId", userId);
            wsMessage.put("timestamp", System.currentTimeMillis());
            chatWebSocketHandler.sendMessageToSession(sessionId, JSON.toJSONString(wsMessage));
        } catch (Exception e) {
            log.warn("WebSocket 广播失败，但不影响主要流程", e);
        }
        log.info("用户 {} 结束会话 {}", userId, sessionId);
        return ResponseUtil.success("会话结束成功");
    }

    @PostMapping(value = "/send-message", consumes = "application/json")
    public ResponseUtil.Result<?> sendMessage(@RequestBody Map<String, Object> params) {
        Long employeeId = UserContext.getCurrentEmployeeId();
        if (employeeId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }
        String sessionId = (String) params.get("sessionId");
        String content = (String) params.get("content");
        Object userIdObj = params.get("userId");
        Long userId = null;
        if (userIdObj != null) {
            try {
                if (userIdObj instanceof String) {
                    userId = Long.parseLong((String) userIdObj);
                } else if (userIdObj instanceof Number) {
                    userId = ((Number) userIdObj).longValue();
                }
            } catch (NumberFormatException e) {
                log.warn("userId 格式错误: {}", userIdObj);
            }
        }
        if (sessionId == null || content == null) {
            return ResponseUtil.error(ResponseCode.PARAM_ERROR);
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null && ChatSession.STATUS_ENDED.equals(session.getStatus())) {
            return ResponseUtil.error("会话已结束，无法发送消息");
        }
        if (session == null) {
            // 禁止凭空建会话：坐席只能向已存在（经转人工/接入建立）的会话发送消息，
            // 否则会绕过 pending→active 状态机，导致会话归属与状态错乱。
            return ResponseUtil.error("会话不存在，请先接入或转人工后再发送消息");
        }
        ChatRecord record = new ChatRecord();
        record.setSessionId(sessionId);
        record.setUserId(userId);
        record.setMessage(content);
        record.setMsgType(BusinessStatus.MSG_TYPE_EMPLOYEE);
        record.setEmployeeId(employeeId);
        record.setIsRead(0);
        record.setConfidence(BigDecimal.ONE);
        record.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(record);
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
            chatWebSocketHandler.sendMessageToSession(sessionId, JSON.toJSONString(wsMessage));
        } catch (Exception e) {
            log.warn("WebSocket 广播失败，但不影响主要流程", e);
        }
        log.info("客服 {} 发送消息到会话 {}: {}", employeeId, sessionId, content);
        return ResponseUtil.success("消息发送成功");
    }

    @GetMapping("/session-id")
    public ResponseUtil.Result<?> getOrCreateSessionId() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }
        String sessionId = chatSessionService.getOrCreateSession(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        return ResponseUtil.success(result);
    }

    @GetMapping("/user-history")
    public ResponseUtil.Result<?> getUserHistory() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }
        String sessionId = chatSessionService.getOrCreateSession(userId);
        LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRecord::getSessionId, sessionId)
                .ne(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_PENDING)
                .orderByDesc(ChatRecord::getCreatedAt)
                .last("LIMIT 10");
        List<ChatRecord> records = chatRecordMapper.selectList(wrapper);
        Collections.reverse(records);
        Map<String, Object> result = new HashMap<>();
        result.put("messages", records);
        return ResponseUtil.success(result);
    }

    @PostMapping(value = "/user-send-message", consumes = "application/json")
    public ResponseUtil.Result<?> userSendMessage(@RequestBody Map<String, Object> params) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }
        String content = (String) params.get("content");
        String sessionId = chatSessionService.getOrCreateSession(userId);
        if (content == null || content.trim().isEmpty()) {
            return ResponseUtil.error(ResponseCode.PARAM_ERROR.getCode(), "消息内容不能为空");
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null && ChatSession.STATUS_ENDED.equals(session.getStatus())) {
            return ResponseUtil.error("会话已结束，无法发送消息");
        }
        ChatRecord record = new ChatRecord();
        record.setSessionId(sessionId);
        record.setUserId(userId);
        record.setMessage(content);
        record.setMsgType(BusinessStatus.MSG_TYPE_USER);
        record.setIsRead(0);
        record.setConfidence(BigDecimal.ONE);
        record.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(record);
        chatSessionService.incrementMessageCount(sessionId);
        chatSessionService.updateLastMessageTime(sessionId);
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "chat");
            wsMessage.put("content", content);
            wsMessage.put("msgType", BusinessStatus.MSG_TYPE_USER);
            wsMessage.put("userId", userId);
            wsMessage.put("timestamp", System.currentTimeMillis());
            chatWebSocketHandler.sendMessageToSession(sessionId, JSON.toJSONString(wsMessage));
        } catch (Exception e) {
            log.warn("WebSocket 广播失败，但不影响主要流程", e);
        }
        log.info("用户 {} 发送消息到会话 {}: {}", userId, sessionId, content);
        return ResponseUtil.success("消息发送成功");
    }
}
