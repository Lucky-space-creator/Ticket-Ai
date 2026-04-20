package com.ticket.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ticket.entity.ChatRecord;
import com.ticket.mapper.ChatRecordMapper;

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
import java.util.Map;
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

    @Resource
    private ChatRecordMapper chatRecordMapper;

    /**
     * 建立连接后触发
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = extractSessionId(session);
        if (sessionId == null) {
            log.warn("连接建立失败，sessionId 为空");
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        sessionPool.computeIfAbsent(sessionId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("WebSocket 连接建立，sessionId: {}, 当前会话数: {}, 连接数: {}", 
                sessionId, sessionPool.size(), getConnectionCount());
    }

    /**
     * 收到消息时触发
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
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
                session.sendMessage(new TextMessage(JSON.toJSONString(Map.of("type", "pong"))));
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
        broadcastMessage(sessionId, JSON.toJSONString(Map.of(
                "type", "chat",
                "content", content,
                "msgType", msgType,
                "employeeId", employeeId,
                "userId", userId,
                "timestamp", System.currentTimeMillis()
        )));
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
    }

    /**
     * 连接关闭后触发
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = extractSessionId(session);
        if (sessionId != null) {
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
