package com.ticket.controller;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.entity.ChatRecord;
import com.ticket.entity.ChatSession;
import com.ticket.enums.BusinessStatus;
import com.ticket.enums.ResponseCode;
import com.ticket.mapper.ChatRecordMapper;
import com.ticket.mapper.ChatSessionMapper;
import com.ticket.service.ChatSessionService;
import com.ticket.handler.ChatWebSocketHandler;
import com.ticket.service.EmployeeService;
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
import java.util.concurrent.*;

/**
 * 客服系统控制器
 * 提供客服工作台相关 API
 */
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

    @Resource
    private EmployeeService employeeService;

    /**
     * 获取待接入的会话列表（用户已请求人工客服但尚未被接待）
     */
    @GetMapping("/pending-sessions")
    public ResponseUtil.Result<?> getPendingSessions() {
        Long employeeId = UserContext.getCurrentEmployeeId();
        if (employeeId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }

        // 使用会话表获取待接入会话
        List<ChatSession> pendingSessions = chatSessionService.getPendingSessions();
        
        List<Map<String, Object>> sessions = pendingSessions.stream().map(session -> {
            // 获取会话的最后一条消息（pending消息）
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
            map.put("unreadCount", 0); // pending会话没有未读消息
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("sessions", sessions);
        return ResponseUtil.success(result);
    }

    /**
     * 获取当前客服正在服务的会话列表
     */
    @GetMapping("/serving-sessions")
    public ResponseUtil.Result<?> getServingSessions() {
        Long employeeId = UserContext.getCurrentEmployeeId();
        if (employeeId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }

        // 使用会话表获取该客服正在服务的活跃会话
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getStatus, ChatSession.STATUS_ACTIVE)
                .orderByDesc(ChatSession::getLastMessageAt);
        List<ChatSession> activeSessions = chatSessionMapper.selectList(wrapper);

        List<Map<String, Object>> sessions = activeSessions.stream().map(session -> {
            // 获取会话的最后一条消息
            LambdaQueryWrapper<ChatRecord> lastMsgWrapper = new LambdaQueryWrapper<>();
            lastMsgWrapper.eq(ChatRecord::getSessionId, session.getId())
                    .orderByDesc(ChatRecord::getCreatedAt)
                    .last("LIMIT 1");
            ChatRecord lastRecord = chatRecordMapper.selectOne(lastMsgWrapper);
            
            // 计算未读消息数（用户发送的未读消息）
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

    /**
     * 获取已结束的会话列表
     */
    @GetMapping("/ended-sessions")
    public ResponseUtil.Result<?> getEndedSessions() {
        Long employeeId = UserContext.getCurrentEmployeeId();
        if (employeeId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }

        // 使用会话表获取该客服已结束的会话
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getEmployeeId, employeeId)
                .eq(ChatSession::getStatus, ChatSession.STATUS_ENDED)
                .orderByDesc(ChatSession::getLastMessageAt);
        List<ChatSession> endedSessions = chatSessionMapper.selectList(wrapper);

        List<Map<String, Object>> sessions = endedSessions.stream().map(session -> {
            // 获取会话的最后一条消息（通常是结束消息）
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
            map.put("unreadCount", 0); // 已结束的会话没有未读消息
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("sessions", sessions);
        return ResponseUtil.success(result);
    }

    /**
     * 客服接入会话（开始服务）
     */
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

        // 检查会话是否已结束（通过会话表）
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null && ChatSession.STATUS_ENDED.equals(session.getStatus())) {
            return ResponseUtil.error("会话已结束，无法再次接入");
        }

        // 1. 更新会话表状态
        if (session == null) {
            // 如果会话不存在，创建新会话（可能是直接创建的情况）
            session = new ChatSession();
            session.setId(sessionId);
            session.setUserId(null); // 可能需要从pending记录中获取
            session.setStatus(ChatSession.STATUS_ACTIVE);
            session.setEmployeeId(employeeId);
            session.setMessageCount(0);
            session.setLastMessageAt(LocalDateTime.now());
            chatSessionMapper.insert(session);
        } else {
            // 更新现有会话
            session.setStatus(ChatSession.STATUS_ACTIVE);
            session.setEmployeeId(employeeId);
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }

        // 2. 将 pending 消息改为客服工号，表示客服已接入
        LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRecord::getSessionId, sessionId)
                .eq(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_PENDING);
        ChatRecord pendingRecord = chatRecordMapper.selectOne(wrapper);
        if (pendingRecord != null) {
            pendingRecord.setMsgType(employeeId.toString()); // 工号作为 msgType
            pendingRecord.setEmployeeId(employeeId);
            pendingRecord.setIsRead(1);
            chatRecordMapper.updateById(pendingRecord);
        }

        // 3. 发送一条系统提示消息
        ChatRecord systemMsg = new ChatRecord();
        systemMsg.setSessionId(sessionId);
        systemMsg.setUserId(pendingRecord != null ? pendingRecord.getUserId() : null);
        systemMsg.setMessage("客服已介入，有什么可以帮助您的？");
        systemMsg.setMsgType(employeeId.toString());
        systemMsg.setEmployeeId(employeeId);
        systemMsg.setIsRead(0);
        systemMsg.setConfidence(BigDecimal.ONE);
        systemMsg.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(systemMsg);

        // 4. 增加会话消息计数
        chatSessionService.incrementMessageCount(sessionId);
        chatSessionService.updateLastMessageTime(sessionId);

        // 5. 通过 WebSocket 广播系统消息
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "chat");
            wsMessage.put("content", "客服已介入，有什么可以帮助您的？");
            wsMessage.put("msgType", employeeId.toString());
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

    /**
     * 结束会话（客服主动结束）
     */
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

        // 检查会话是否已结束（通过会话表）
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null && ChatSession.STATUS_ENDED.equals(session.getStatus())) {
            return ResponseUtil.success("会话已结束");
        }

        // 1. 更新会话表状态
        if (session == null) {
            // 如果会话不存在，创建已结束的会话记录
            session = new ChatSession();
            session.setId(sessionId);
            session.setEmployeeId(employeeId);
            session.setStatus(ChatSession.STATUS_ENDED);
            session.setMessageCount(0);
            session.setLastMessageAt(LocalDateTime.now());
            chatSessionMapper.insert(session);
        } else {
            // 更新现有会话状态
            session.setStatus(ChatSession.STATUS_ENDED);
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }

        // 2. 发送一条结束会话的系统消息
        ChatRecord endMsg = new ChatRecord();
        endMsg.setSessionId(sessionId);
        endMsg.setMessage("客服会话已结束，若还需要客服接入，请点击转客服按钮");
        endMsg.setMsgType(BusinessStatus.MSG_TYPE_ENDED);
        endMsg.setEmployeeId(employeeId);
        endMsg.setIsRead(0);
        endMsg.setConfidence(BigDecimal.ONE);
        endMsg.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(endMsg);

        // 3. 增加会话消息计数
        chatSessionService.incrementMessageCount(sessionId);
        chatSessionService.updateLastMessageTime(sessionId);

        // 4. 通过 WebSocket 广播结束消息
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "chat");
            wsMessage.put("content", "客服会话已结束，若还需要客服接入，请点击转客服按钮");
            wsMessage.put("msgType", BusinessStatus.MSG_TYPE_ENDED);
            wsMessage.put("employeeId", employeeId);
            // userId 为 null，不添加到消息中
            wsMessage.put("timestamp", System.currentTimeMillis());
            chatWebSocketHandler.sendMessageToSession(sessionId, JSON.toJSONString(wsMessage));
        } catch (Exception e) {
            log.warn("WebSocket 广播失败，但不影响主要流程", e);
        }

        log.info("客服 {} 结束会话 {}", employeeId, sessionId);
        return ResponseUtil.success("会话结束成功");
    }

    /**
     * 获取指定会话的历史消息
     */
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

        // 标记用户消息为已读
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

    /**
     * 用户转人工客服请求（供前端用户端调用）
     */
    @PostMapping(value = "/request-human", consumes = "application/json")
    public ResponseUtil.Result<?> requestHumanService(@RequestBody Map<String, String> params) {
        log.debug("收到转人工请求，参数: {}", params);
        String sessionId = params.get("sessionId");
        String reason = params.get("reason");
        Long userId = UserContext.getCurrentUserId();
        log.debug("当前用户ID: {}", userId);
        if (userId == null) {
            log.warn("用户未登录，无法转人工");
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }
        // 获取或创建会话ID（如果未提供）
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = chatSessionService.getOrCreateSession(userId);
        }
        // 确保会话存在并更新为pending状态
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            // 会话不存在，创建新的pending会话
            session = new ChatSession();
            session.setId(sessionId);
            session.setUserId(userId);
            session.setStatus(ChatSession.STATUS_PENDING);
            session.setTitle("用户请求转人工客服");
            session.setMessageCount(0);
            session.setLastMessageAt(LocalDateTime.now());
            chatSessionMapper.insert(session);
        } else if (!ChatSession.STATUS_PENDING.equals(session.getStatus())) {
            // 如果会话存在但不是pending状态，更新为pending
            session.setStatus(ChatSession.STATUS_PENDING);
            session.setTitle("用户请求转人工客服");
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }

        // 构建消息内容，包含转接原因
        String message = "用户请求转人工客服";
        if (reason != null && !reason.trim().isEmpty()) {
            message += "，原因：" + reason.trim();
        }

        // 保存一条 pending 消息，表示用户请求人工客服
        ChatRecord pendingMsg = new ChatRecord();
        pendingMsg.setSessionId(sessionId);
        pendingMsg.setUserId(userId);
        pendingMsg.setMessage(message);
        pendingMsg.setMsgType(BusinessStatus.MSG_TYPE_PENDING);
        pendingMsg.setIsRead(0);
        pendingMsg.setConfidence(BigDecimal.ONE);
        pendingMsg.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(pendingMsg);

        // 增加会话消息计数
        chatSessionService.incrementMessageCount(sessionId);
        chatSessionService.updateLastMessageTime(sessionId);

        // 发送全局通知给所有客服
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "notification");
            wsMessage.put("notificationType", "new_pending_session");
            wsMessage.put("sessionId", sessionId);
            if (userId != null) {
                wsMessage.put("userId", userId);
            }
            wsMessage.put("content", message);
            wsMessage.put("timestamp", System.currentTimeMillis());
            String notificationJson = JSON.toJSONString(wsMessage);
            log.info("发送转人工全局通知，会话ID: {}, 用户ID: {}, 消息: {}", sessionId, userId, message);
            chatWebSocketHandler.sendMessageToGlobal(notificationJson);
            log.debug("转人工全局通知已发送");
        } catch (Exception e) {
            log.warn("发送全局通知失败，但不影响主要流程", e);
        }

        // 延迟5秒后尝试自动分配客服，如果会话仍为pending状态
        final String finalSessionId = sessionId;
        final Long finalUserId = userId;
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            try {
                // 检查会话是否仍为pending（未被客服手动接入）
                ChatSession currentSession = chatSessionMapper.selectById(finalSessionId);
                if (currentSession != null && ChatSession.STATUS_PENDING.equals(currentSession.getStatus())) {
                    // 尝试自动分配客服
                    Long employeeId = employeeService.findAvailableEmployee();
                    if (employeeId != null) {
                        // 分配成功，直接接入会话
                        boolean accepted = chatSessionService.acceptSession(finalSessionId, employeeId);
                        if (accepted) {
                            // 发送系统消息，通知客服已接入
                            ChatRecord systemMsg = new ChatRecord();
                            systemMsg.setSessionId(finalSessionId);
                            systemMsg.setUserId(finalUserId);
                            systemMsg.setMessage("客服已介入，有什么可以帮助您的？");
                            systemMsg.setMsgType(employeeId.toString());
                            systemMsg.setEmployeeId(employeeId);
                            systemMsg.setIsRead(0);
                            systemMsg.setConfidence(BigDecimal.ONE);
                            systemMsg.setCreatedAt(LocalDateTime.now());
                            chatRecordMapper.insert(systemMsg);
                            // 增加会话消息计数
                            chatSessionService.incrementMessageCount(finalSessionId);
                            chatSessionService.updateLastMessageTime(finalSessionId);
                            // 通过 WebSocket 广播系统消息
                            Map<String, Object> wsMessage = new HashMap<>();
                            wsMessage.put("type", "chat");
                            wsMessage.put("content", "客服已介入，有什么可以帮助您的？");
                            wsMessage.put("msgType", employeeId.toString());
                            wsMessage.put("employeeId", employeeId);
                            if (finalUserId != null) {
                                wsMessage.put("userId", finalUserId);
                            }
                            wsMessage.put("timestamp", System.currentTimeMillis());
                            chatWebSocketHandler.sendMessageToSession(finalSessionId, JSON.toJSONString(wsMessage));
                            log.info("延迟自动分配客服 {} 接入会话 {}", employeeId, finalSessionId);
                        }
                    } else {
                        // 无空闲客服，发送提示消息给用户
                        ChatRecord busyMsg = new ChatRecord();
                        busyMsg.setSessionId(finalSessionId);
                        busyMsg.setUserId(finalUserId);
                        busyMsg.setMessage("当前客服忙，请稍后再试或继续使用AI助手。");
                        busyMsg.setMsgType(BusinessStatus.MSG_TYPE_ROBOT);
                        busyMsg.setIsRead(0);
                        busyMsg.setConfidence(BigDecimal.ONE);
                        busyMsg.setCreatedAt(LocalDateTime.now());
                        chatRecordMapper.insert(busyMsg);
                        chatSessionService.incrementMessageCount(finalSessionId);
                        chatSessionService.updateLastMessageTime(finalSessionId);
                        // 将会话状态改为AI_ONLY（转人工失败）
                        ChatSession sessionToUpdate = chatSessionMapper.selectById(finalSessionId);
                        if (sessionToUpdate != null) {
                            sessionToUpdate.setStatus(ChatSession.STATUS_AI_ONLY);
                            chatSessionMapper.updateById(sessionToUpdate);
                        }
                        // 通过 WebSocket 广播提示消息
                        Map<String, Object> wsMessage = new HashMap<>();
                        wsMessage.put("type", "chat");
                        wsMessage.put("content", "当前客服忙，请稍后再试或继续使用AI助手。");
                        wsMessage.put("msgType", BusinessStatus.MSG_TYPE_ROBOT);
                        // employeeId 为 null，不添加到消息中
                        if (finalUserId != null) {
                            wsMessage.put("userId", finalUserId);
                        }
                        wsMessage.put("timestamp", System.currentTimeMillis());
                        chatWebSocketHandler.sendMessageToSession(finalSessionId, JSON.toJSONString(wsMessage));
                        log.info("客服忙，转人工失败，会话 {} 恢复AI对话", finalSessionId);
                    }
                } else {
                    // 会话已被客服手动接入，无需自动分配
                    log.debug("会话 {} 已被客服手动接入，跳过自动分配", finalSessionId);
                }
            } catch (Exception e) {
                log.warn("延迟自动分配客服失败", e);
            } finally {
                scheduler.shutdown();
            }
        }, 5, TimeUnit.SECONDS);

        log.info("用户 {} 请求转人工客服，会话ID: {}，原因: {}", userId, sessionId, reason);
        return ResponseUtil.success("转人工请求已提交");
    }

    /**
     * 测试端点，用于验证 API 是否可访问
     */
    @GetMapping("/test")
    public ResponseUtil.Result<?> testEndpoint() {
        log.debug("测试端点被调用");
        return ResponseUtil.success("客服API工作正常");
    }

    /**
     * 用户结束会话（用户主动结束）
     */
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

        // 检查会话是否已结束（通过会话表）
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null && ChatSession.STATUS_ENDED.equals(session.getStatus())) {
            return ResponseUtil.success("会话已结束");
        }

        // 1. 更新会话表状态
        if (session == null) {
            // 如果会话不存在，创建已结束的会话记录
            session = new ChatSession();
            session.setId(sessionId);
            session.setUserId(userId);
            session.setStatus(ChatSession.STATUS_ENDED);
            session.setMessageCount(0);
            session.setLastMessageAt(LocalDateTime.now());
            chatSessionMapper.insert(session);
        } else {
            // 更新现有会话状态
            session.setStatus(ChatSession.STATUS_ENDED);
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }

        // 2. 发送一条结束会话的系统消息
        ChatRecord endMsg = new ChatRecord();
        endMsg.setSessionId(sessionId);
        endMsg.setUserId(userId);
        endMsg.setMessage("用户已结束本次会话");
        endMsg.setMsgType(BusinessStatus.MSG_TYPE_ENDED);
        endMsg.setIsRead(0);
        endMsg.setConfidence(BigDecimal.ONE);
        endMsg.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(endMsg);

        // 3. 增加会话消息计数
        chatSessionService.incrementMessageCount(sessionId);
        chatSessionService.updateLastMessageTime(sessionId);

        // 4. 通过 WebSocket 广播结束消息
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "chat");
            wsMessage.put("content", "用户已结束本次会话");
            wsMessage.put("msgType", BusinessStatus.MSG_TYPE_ENDED);
            // employeeId 为 null，不添加到消息中
            if (userId != null) {
                wsMessage.put("userId", userId);
            }
            wsMessage.put("timestamp", System.currentTimeMillis());
            chatWebSocketHandler.sendMessageToSession(sessionId, JSON.toJSONString(wsMessage));
        } catch (Exception e) {
            log.warn("WebSocket 广播失败，但不影响主要流程", e);
        }

        log.info("用户 {} 结束会话 {}", userId, sessionId);
        return ResponseUtil.success("会话结束成功");
    }

    /**
     * 发送客服消息（供前端客服端调用，通过 REST 而非 WebSocket）
     */
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

        // 安全转换 userId
        if (userIdObj != null) {
            try {
                if (userIdObj instanceof String) {
                    userId = Long.parseLong((String) userIdObj);
                } else if (userIdObj instanceof Number) {
                    userId = ((Number) userIdObj).longValue();
                } else {
                    log.warn("无法识别的 userId 类型: {}", userIdObj.getClass().getName());
                }
            } catch (NumberFormatException e) {
                log.warn("userId 格式错误: {}", userIdObj);
            }
        }

        if (sessionId == null || content == null) {
            return ResponseUtil.error(ResponseCode.PARAM_ERROR);
        }

        // 检查会话是否已结束（通过会话表）
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null && ChatSession.STATUS_ENDED.equals(session.getStatus())) {
            return ResponseUtil.error("会话已结束，无法发送消息");
        }

        // 如果会话不存在，创建新会话（理论上应该存在）
        if (session == null) {
            session = new ChatSession();
            session.setId(sessionId);
            session.setUserId(userId);
            session.setEmployeeId(employeeId);
            session.setStatus(ChatSession.STATUS_ACTIVE);
            session.setMessageCount(0);
            session.setLastMessageAt(LocalDateTime.now());
            chatSessionMapper.insert(session);
        }

        ChatRecord record = new ChatRecord();
        record.setSessionId(sessionId);
        record.setUserId(userId);
        record.setMessage(content);
        record.setMsgType(employeeId.toString()); // 客服工号作为 msgType
        record.setEmployeeId(employeeId);
        record.setIsRead(0);
        record.setConfidence(BigDecimal.ONE);
        record.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(record);

        // 增加会话消息计数
        chatSessionService.incrementMessageCount(sessionId);
        chatSessionService.updateLastMessageTime(sessionId);

        // 通过 WebSocket 广播消息
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "chat");
            wsMessage.put("content", content);
            wsMessage.put("msgType", employeeId.toString());
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

    /**
     * 获取或创建当前用户的会话ID
     * @return 会话ID
     */
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

    /**
     * 获取用户聊天历史（用户端调用）
     */
    @GetMapping("/user-history")
    public ResponseUtil.Result<?> getUserHistory() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }
        
        String sessionId = chatSessionService.getOrCreateSession(userId);
        
        // 获取最近10条消息，按时间升序，排除pending消息
        LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRecord::getSessionId, sessionId)
                .ne(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_PENDING)
                // 按时间降序 可以获得最新消息，否则按时间升序 可以获得最老消息
                .orderByDesc(ChatRecord::getCreatedAt)
                .last("LIMIT 10");
        List<ChatRecord> records = chatRecordMapper.selectList(wrapper);
        //数组反转顺序
        Collections.reverse(records);
        
        Map<String, Object> result = new HashMap<>();
        result.put("messages", records);
        return ResponseUtil.success(result);
    }

    /**
     * 用户发送消息给客服（用户端调用）
     */
    @PostMapping(value = "/user-send-message", consumes = "application/json")
    public ResponseUtil.Result<?> userSendMessage(@RequestBody Map<String, Object> params) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }

        String content = (String) params.get("content");
        // 获取或创建会话ID
        String sessionId = chatSessionService.getOrCreateSession(userId);

        // 检查消息内容是否为空
        if (content == null || content.trim().isEmpty()) {
            return ResponseUtil.error(ResponseCode.PARAM_ERROR.getCode(), "消息内容不能为空");
        }

        // 检查会话是否已结束（通过会话表）
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null && ChatSession.STATUS_ENDED.equals(session.getStatus())) {
            return ResponseUtil.error("会话已结束，无法发送消息");
        }

        // 会话应该已经存在（由getOrCreateSession创建）

        // 保存用户消息
        ChatRecord record = new ChatRecord();
        record.setSessionId(sessionId);
        record.setUserId(userId);
        record.setMessage(content);
        record.setMsgType(BusinessStatus.MSG_TYPE_USER);
        record.setIsRead(0);
        record.setConfidence(BigDecimal.ONE);
        record.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(record);

        // 增加会话消息计数
        chatSessionService.incrementMessageCount(sessionId);
        chatSessionService.updateLastMessageTime(sessionId);

        // 通过 WebSocket 广播消息给客服端
        try {
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "chat");
            wsMessage.put("content", content);
            wsMessage.put("msgType", BusinessStatus.MSG_TYPE_USER);
            // employeeId 为 null，不添加到消息中
            if (userId != null) {
                wsMessage.put("userId", userId);
            }
            wsMessage.put("timestamp", System.currentTimeMillis());
            chatWebSocketHandler.sendMessageToSession(sessionId, JSON.toJSONString(wsMessage));
        } catch (Exception e) {
            log.warn("WebSocket 广播失败，但不影响主要流程", e);
        }

        log.info("用户 {} 发送消息到会话 {}: {}", userId, sessionId, content);
        return ResponseUtil.success("消息发送成功");
    }
}
