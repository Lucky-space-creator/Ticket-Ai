package com.ticket.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ticket.entity.ChatRecord;
import com.ticket.mapper.ChatRecordMapper;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 聊天 WebSocket 处理器
 * 管理连接池、处理消息收发、持久化消息到数据库
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    /**
     * 存储 sessionId -> WebSocketSession 集合的映射
     * 同一个会话的用户和客服共享一个 sessionId，但各自建立连接
     */
    private final Map<String, Set<WebSocketSession>> sessionPool = new ConcurrentHashMap<>();

    /**
     * 全局连接池，用于客服接收全局通知
     * 客服连接到 /ws/chat/global 时会加入此集合
     */
    private final Set<WebSocketSession> globalSessions = new CopyOnWriteArraySet<>();

    /**
     * 全局会话ID常量
     */
    private static final String GLOBAL_SESSION_ID = "global";

    @Resource
    private ChatRecordMapper chatRecordMapper;

    /**
     * 建立连接后触发
     */
    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) throws Exception {
        String sessionId = extractSessionId(session);
        if (sessionId == null) {
            log.warn("连接建立失败，sessionId 为空");
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        
        // 全局连接特殊处理
        if (GLOBAL_SESSION_ID.equals(sessionId)) {
            globalSessions.add(session);
            log.info("WebSocket 全局连接建立，当前全局连接数: {}", globalSessions.size());
        } else {
            sessionPool.computeIfAbsent(sessionId, k -> new CopyOnWriteArraySet<>()).add(session);
            log.info("WebSocket 连接建立，sessionId: {}, 当前会话数: {}, 连接数: {}", 
                    sessionId, sessionPool.size(), getConnectionCount());
        }
    }

    /**
     * 收到消息时触发
     */
    @Override
    protected void handleTextMessage(@NotNull WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        log.debug("收到 WebSocket 消息: {}", payload);

        try {
            JSONObject json = JSON.parseObject(payload);
            String type = json.getString("type");
            String sessionId = extractSessionId(session);

            if ("chat".equals(type)) {
                // 聊天消息
                handleChatMessage(json, sessionId);
            } else if ("heartbeat".equals(type)) {
                // 心跳包，回复 pong
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

    /**
     * 处理聊天消息
     */
    private void handleChatMessage(JSONObject json, String sessionId) {
        String content = json.getString("content");
        Long userId = json.getLong("userId");
        String msgType = json.getString("msgType");
        Long employeeId = json.getLong("employeeId");

        // 基本验证
        if (content == null || content.trim().isEmpty()) {
            log.warn("收到空消息内容，忽略处理");
            return;
        }
        if (msgType == null) {
            msgType = "unknown";
        }

        // 保存到数据库
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
        log.debug("保存聊天记录成功，会话ID: {}, 消息类型: {}", sessionId, msgType);

        // 广播给该会话的所有连接（用户和客服）
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

        // 如果是 pending 消息（用户请求人工客服），发送全局通知给所有客服
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

    /**
     * 广播消息给指定会话的所有连接
     */
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
        
        // 同时发送到全局连接，以便客服端接收
        try {
            // 解析消息，添加会话ID
            com.alibaba.fastjson2.JSONObject json = com.alibaba.fastjson2.JSON.parseObject(message);
            json.put("sessionId", sessionId);
            json.put("notificationType", "chat_message");
            sendMessageToGlobal(com.alibaba.fastjson2.JSON.toJSONString(json));
        } catch (Exception e) {
            log.warn("发送到全局连接失败", e);
        }
    }

    /**
     * 连接关闭后触发
     */
    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) throws Exception {
        String sessionId = extractSessionId(session);
        if (sessionId != null) {
            // 全局连接特殊处理
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

    /**
     * 从 WebSocketSession 中提取 sessionId
     */
    private String extractSessionId(WebSocketSession session) {
        // 从 URI 模板变量中获取 {sessionId}
        if (session.getUri() == null) {
            log.warn("WebSocketSession URI 为空");
            return null;
        }
        String path = session.getUri().getPath();
        // 路径格式: /ws/chat/{sessionId}
        String[] parts = path.split("/");
        if (parts.length >= 4) {
            return parts[3];
        }
        return null;
    }

    /**
     * 发送消息给指定会话的所有连接
     */
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
        
        // 同时发送到全局连接，以便客服端接收
        try {
            // 解析消息，添加会话ID
            JSONObject json = JSON.parseObject(message);
            json.put("sessionId", sessionId);
            json.put("notificationType", "chat_message");
            sendMessageToGlobal(JSON.toJSONString(json));
        } catch (Exception e) {
            log.warn("发送到全局连接失败", e);
        }
    }

    /**
     * 发送消息给所有全局连接（客服工作台）
     */
    public void sendMessageToGlobal(String message) {
        for (WebSocketSession targetSession : globalSessions) {
            if (targetSession.isOpen()) {
                try {
                    targetSession.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    log.error("发送全局消息失败", e);
                }
            }
        }
    }

    /**
     * 获取当前连接总数（所有会话的连接数之和）
     */
    public int getConnectionCount() {
        return sessionPool.values().stream()
                .mapToInt(Set::size)
                .sum();
    }
}
