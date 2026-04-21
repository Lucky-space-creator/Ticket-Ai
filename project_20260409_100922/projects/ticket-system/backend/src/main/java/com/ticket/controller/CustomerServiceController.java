package com.ticket.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.entity.ChatRecord;
import com.ticket.enums.BusinessStatus;
import com.ticket.enums.ResponseCode;
import com.ticket.mapper.ChatRecordMapper;
import com.ticket.handler.ChatWebSocketHandler;
import com.ticket.util.ResponseUtil;
import com.ticket.util.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private ChatWebSocketHandler chatWebSocketHandler;

    /**
     * 获取待接入的会话列表（用户已请求人工客服但尚未被接待）
     */
    @GetMapping("/pending-sessions")
    public ResponseUtil.Result<?> getPendingSessions() {
        Long employeeId = UserContext.getCurrentEmployeeId();
        if (employeeId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }

        LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_PENDING)
                .orderByDesc(ChatRecord::getCreatedAt);
        List<ChatRecord> records = chatRecordMapper.selectList(wrapper);

        List<Map<String, Object>> sessions = records.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                ChatRecord::getSessionId,
                                record -> {
                                    Map<String, Object> map = new HashMap<>();
                                    map.put("sessionId", record.getSessionId());
                                    map.put("userId", record.getUserId());
                                    map.put("lastMessage", record.getMessage());
                                    map.put("lastMessageTime", record.getCreatedAt());
                                    map.put("unreadCount", 0);
                                    return map;
                                },
                                (existing, replacement) -> existing
                        ),
                        map -> map.values().stream().collect(Collectors.toList())
                ));

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

        // 找出所有包含该客服员工ID的消息（排除pending和ended类型）
        LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRecord::getEmployeeId, employeeId)
                .ne(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_PENDING)
                .ne(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_ENDED)
                .orderByDesc(ChatRecord::getCreatedAt);
        List<ChatRecord> allRecords = chatRecordMapper.selectList(wrapper);

        // 按会话ID分组，取每个会话的最新一条记录
        Map<String, ChatRecord> latestRecords = allRecords.stream()
                .collect(Collectors.toMap(
                        ChatRecord::getSessionId,
                        record -> record,
                        (existing, replacement) -> existing // 保留第一个（因为已按时间降序排序）
                ));

        List<Map<String, Object>> sessions = latestRecords.values().stream().map(record -> {
            // 计算未读消息数（用户发送的未读消息）
            LambdaQueryWrapper<ChatRecord> unreadWrapper = new LambdaQueryWrapper<>();
            unreadWrapper.eq(ChatRecord::getSessionId, record.getSessionId())
                    .eq(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_USER)
                    .eq(ChatRecord::getIsRead, 0);
            int unreadCount = chatRecordMapper.selectCount(unreadWrapper).intValue();

            Map<String, Object> map = new HashMap<>();
            map.put("sessionId", record.getSessionId());
            map.put("userId", record.getUserId());
            map.put("lastMessage", record.getMessage());
            map.put("lastMessageTime", record.getCreatedAt());
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

        // 找出该客服已结束的会话（msg_type为ended，且employee_id为该客服）
        LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRecord::getEmployeeId, employeeId)
                .eq(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_ENDED)
                .orderByDesc(ChatRecord::getCreatedAt);
        List<ChatRecord> allRecords = chatRecordMapper.selectList(wrapper);

        // 按会话ID分组，取每个会话的最新一条记录
        Map<String, ChatRecord> latestRecords = allRecords.stream()
                .collect(Collectors.toMap(
                        ChatRecord::getSessionId,
                        record -> record,
                        (existing, replacement) -> existing // 保留第一个（因为已按时间降序排序）
                ));

        List<Map<String, Object>> sessions = latestRecords.values().stream().map(record -> {
            Map<String, Object> map = new HashMap<>();
            map.put("sessionId", record.getSessionId());
            map.put("userId", record.getUserId());
            map.put("lastMessage", record.getMessage());
            map.put("lastMessageTime", record.getCreatedAt());
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

        // 检查会话是否已结束
        LambdaQueryWrapper<ChatRecord> endedWrapper = new LambdaQueryWrapper<>();
        endedWrapper.eq(ChatRecord::getSessionId, sessionId)
                .eq(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_ENDED);
        if (chatRecordMapper.selectCount(endedWrapper) > 0) {
            return ResponseUtil.error("会话已结束，无法再次接入");
        }

        // 1. 将 pending 消息改为客服工号，表示客服已接入
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

        // 2. 发送一条系统提示消息
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

        // 3. 通过 WebSocket 广播系统消息
        try {
            chatWebSocketHandler.sendMessageToSession(sessionId, com.alibaba.fastjson2.JSON.toJSONString(Map.of(
                    "type", "chat",
                    "content", "客服已介入，有什么可以帮助您的？",
                    "msgType", employeeId.toString(),
                    "employeeId", employeeId,
                    "userId", pendingRecord != null ? pendingRecord.getUserId() : null,
                    "timestamp", System.currentTimeMillis()
            )));
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

        // 检查会话是否已结束，避免重复插入
        LambdaQueryWrapper<ChatRecord> endedWrapper = new LambdaQueryWrapper<>();
        endedWrapper.eq(ChatRecord::getSessionId, sessionId)
                .eq(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_ENDED);
        if (chatRecordMapper.selectCount(endedWrapper) > 0) {
            return ResponseUtil.success("会话已结束");
        }

        // 发送一条结束会话的系统消息
        ChatRecord endMsg = new ChatRecord();
        endMsg.setSessionId(sessionId);
        endMsg.setMessage("客服会话已结束，若还需要客服接入，请点击转客服按钮");
        endMsg.setMsgType(BusinessStatus.MSG_TYPE_ENDED);
        endMsg.setEmployeeId(employeeId);
        endMsg.setIsRead(0);
        endMsg.setConfidence(BigDecimal.ONE);
        endMsg.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(endMsg);

        // 通过 WebSocket 广播结束消息
        try {
            chatWebSocketHandler.sendMessageToSession(sessionId, com.alibaba.fastjson2.JSON.toJSONString(Map.of(
                    "type", "chat",
                    "content", "客服会话已结束，若还需要客服接入，请点击转客服按钮",
                    "msgType", BusinessStatus.MSG_TYPE_ENDED,
                    "employeeId", employeeId,
                    "userId", null,
                    "timestamp", System.currentTimeMillis()
            )));
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
        // 如果未提供 sessionId，则根据用户ID生成
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "user_" + userId;
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
            sessionId = "user_" + userId;
        }

        // 检查会话是否已结束
        LambdaQueryWrapper<ChatRecord> endedWrapper = new LambdaQueryWrapper<>();
        endedWrapper.eq(ChatRecord::getSessionId, sessionId)
                .eq(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_ENDED);
        if (chatRecordMapper.selectCount(endedWrapper) > 0) {
            return ResponseUtil.success("会话已结束");
        }

        // 发送一条结束会话的系统消息
        ChatRecord endMsg = new ChatRecord();
        endMsg.setSessionId(sessionId);
        endMsg.setUserId(userId);
        endMsg.setMessage("用户已结束本次会话");
        endMsg.setMsgType(BusinessStatus.MSG_TYPE_ENDED);
        endMsg.setIsRead(0);
        endMsg.setConfidence(BigDecimal.ONE);
        endMsg.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(endMsg);

        // 通过 WebSocket 广播结束消息
        try {
            chatWebSocketHandler.sendMessageToSession(sessionId, com.alibaba.fastjson2.JSON.toJSONString(Map.of(
                    "type", "chat",
                    "content", "用户已结束本次会话",
                    "msgType", BusinessStatus.MSG_TYPE_ENDED,
                    "employeeId", null,
                    "userId", userId,
                    "timestamp", System.currentTimeMillis()
            )));
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
                } else if (userIdObj instanceof Long) {
                    userId = (Long) userIdObj;
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

        // 检查会话是否已结束
        LambdaQueryWrapper<ChatRecord> endedWrapper = new LambdaQueryWrapper<>();
        endedWrapper.eq(ChatRecord::getSessionId, sessionId)
                .eq(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_ENDED);
        if (chatRecordMapper.selectCount(endedWrapper) > 0) {
            return ResponseUtil.error("会话已结束，无法发送消息");
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

        // 通过 WebSocket 广播消息
        try {
            chatWebSocketHandler.sendMessageToSession(sessionId, com.alibaba.fastjson2.JSON.toJSONString(Map.of(
                    "type", "chat",
                    "content", content,
                    "msgType", employeeId.toString(),
                    "employeeId", employeeId,
                    "userId", userId,
                    "timestamp", System.currentTimeMillis()
            )));
        } catch (Exception e) {
            log.warn("WebSocket 广播失败，但不影响主要流程", e);
        }

        log.info("客服 {} 发送消息到会话 {}: {}", employeeId, sessionId, content);
        return ResponseUtil.success("消息发送成功");
    }
}
