package com.ticket.customer.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ticket.customer.mapper.ChatRecordMapper;
import com.ticket.entity.ChatRecord;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String GLOBAL_SESSION_ID = "global";

    private final Map<String, Set<WebSocketSession>> sessionPool = new ConcurrentHashMap<>();
    private final Set<WebSocketSession> globalSessions = new CopyOnWriteArraySet<>();

    @Resource
    private ChatRecordMapper chatRecordMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = extractSessionId(session);
        if (sessionId == null) {
            log.warn("连接建立失败，sessionId 为空");
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        if (GLOBAL_SESSION_ID.equals(sessionId)) {
            globalSessions.add(session);
            log.info("WebSocket 全局连接建立，当前全局连接数: {}", globalSessions.size());
        } else {
            sessionPool.computeIfAbsent(sessionId, k -> new CopyOnWriteArraySet<>()).add(session);
            log.info("WebSocket 连接建立，sessionId: {}, 当前会话数: {}, 连接数: {}",
                    sessionId, sessionPool.size(), getConnectionCount());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        log.debug("收到 WebSocket 消息: {}", payload);
        try {
            JSONObject json = JSON.parseObject(payload);
            String type = json.getString("type");
            String sessionId = extractSessionId(session);
            if ("chat".equals(type)) {
                handleChatMessage(json, sessionId);
            } else if ("heartbeat".equals(type)) {
                Map<String, String> pongMsg = new HashMap<>();
                pongMsg.put("type", "pong");
                session.sendMessage(new TextMessage(JSON.toJSONString(pongMsg)));
            } else {
                log.warn("未知的消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("处理消息时发生异常", e);
        }
    }

    private void handleChatMessage(JSONObject json, String sessionId) {
        String content = json.getString("content");
        Long userId = json.getLong("userId");
        String msgType = json.getString("msgType");
        Long employeeId = json.getLong("employeeId");
        if (content == null || content.trim().isEmpty()) {
            log.warn("收到空消息内容，忽略处理");
            return;
        }
        if (msgType == null) {
            msgType = "unknown";
        }
        ChatRecord record = new ChatRecord();
        record.setSessionId(sessionId);
        record.setMessage(content);
        record.setMsgType(msgType);
        record.setUserId(userId);
        record.setEmployeeId(employeeId);
        record.setIsRead(0);
        record.setConfidence(BigDecimal.ONE);
        record.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(record);

        Map<String, Object> wsMessage = new HashMap<>();
        wsMessage.put("type", "chat");
        wsMessage.put("content", content);
        wsMessage.put("msgType", msgType);
        if (employeeId != null) {
            wsMessage.put("employeeId", employeeId);
        }
        if (userId != null) {
            wsMessage.put("userId", userId);
        }
        wsMessage.put("timestamp", System.currentTimeMillis());
        broadcastMessage(sessionId, JSON.toJSONString(wsMessage));

        if ("pending".equals(msgType)) {
            Map<String, Object> notificationMsg = new HashMap<>();
            notificationMsg.put("type", "notification");
            notificationMsg.put("notificationType", "new_pending_session");
            notificationMsg.put("sessionId", sessionId);
            if (userId != null) {
                notificationMsg.put("userId", userId);
            }
            notificationMsg.put("content", content);
            notificationMsg.put("timestamp", System.currentTimeMillis());
            sendMessageToGlobal(JSON.toJSONString(notificationMsg));
        }
    }

    private void broadcastMessage(String sessionId, String message) {
        Set<WebSocketSession> sessions = sessionPool.get(sessionId);
        if (sessions != null) {
            for (WebSocketSession targetSession : sessions) {
                if (targetSession.isOpen()) {
                    try {
                        targetSession.sendMessage(new TextMessage(message));
                    } catch (IOException e) {
                        log.error("发送消息失败", e);
                    }
                }
            }
        }
        try {
            JSONObject json = JSON.parseObject(message);
            json.put("sessionId", sessionId);
            json.put("notificationType", "chat_message");
            sendMessageToGlobal(JSON.toJSONString(json));
        } catch (Exception e) {
            log.warn("发送到全局连接失败", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = extractSessionId(session);
        if (sessionId != null) {
            if (GLOBAL_SESSION_ID.equals(sessionId)) {
                globalSessions.remove(session);
                log.info("WebSocket 全局连接关闭，关闭原因: {}, 当前全局连接数: {}", status, globalSessions.size());
            } else {
                Set<WebSocketSession> sessions = sessionPool.get(sessionId);
                if (sessions != null) {
                    sessions.remove(session);
                    if (sessions.isEmpty()) {
                        sessionPool.remove(sessionId);
                    }
                }
                log.info("WebSocket 连接关闭，sessionId: {}, 关闭原因: {}, 当前会话数: {}, 连接数: {}",
                        sessionId, status, sessionPool.size(), getConnectionCount());
            }
        }
    }

    private String extractSessionId(WebSocketSession session) {
        if (session.getUri() == null) {
            log.warn("WebSocketSession URI 为空");
            return null;
        }
        String path = session.getUri().getPath();
        String[] parts = path.split("/");
        if (parts.length >= 4) {
            return parts[3];
        }
        return null;
    }

    public void sendMessageToSession(String sessionId, String message) {
        Set<WebSocketSession> sessions = sessionPool.get(sessionId);
        if (sessions != null) {
            for (WebSocketSession targetSession : sessions) {
                if (targetSession.isOpen()) {
                    try {
                        targetSession.sendMessage(new TextMessage(message));
                    } catch (IOException e) {
                        log.error("发送消息失败", e);
                    }
                }
            }
        }
        try {
            JSONObject json = JSON.parseObject(message);
            json.put("sessionId", sessionId);
            json.put("notificationType", "chat_message");
            sendMessageToGlobal(JSON.toJSONString(json));
        } catch (Exception e) {
            log.warn("发送到全局连接失败", e);
        }
    }

    public void sendMessageToGlobal(String message) {
        log.debug("发送全局消息，当前全局连接数: {}，消息内容: {}", globalSessions.size(), message);
        int sentCount = 0;
        for (WebSocketSession targetSession : globalSessions) {
            if (targetSession.isOpen()) {
                try {
                    targetSession.sendMessage(new TextMessage(message));
                    sentCount++;
                } catch (IOException e) {
                    log.error("发送全局消息失败", e);
                }
            }
        }
        log.debug("全局消息发送完成，成功发送给 {} 个连接", sentCount);
    }

    public int getConnectionCount() {
        return sessionPool.values().stream()
                .mapToInt(Set::size)
                .sum();
    }
}
