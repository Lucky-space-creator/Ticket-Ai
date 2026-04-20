package com.ticket.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.entity.ChatRecord;
import com.ticket.enums.BusinessStatus;
import com.ticket.enums.ResponseCode;
import com.ticket.mapper.ChatRecordMapper;
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

        // 找出所有包含该客服员工ID的消息（排除pending类型）
        LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRecord::getEmployeeId, employeeId)
                .ne(ChatRecord::getMsgType, BusinessStatus.MSG_TYPE_PENDING)
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
        systemMsg.setMessage("客服已接入，开始为您服务。");
        systemMsg.setMsgType(employeeId.toString());
        systemMsg.setEmployeeId(employeeId);
        systemMsg.setIsRead(0);
        systemMsg.setConfidence(BigDecimal.ONE);
        systemMsg.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(systemMsg);

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

        // 发送一条结束会话的系统消息
        ChatRecord endMsg = new ChatRecord();
        endMsg.setSessionId(sessionId);
        endMsg.setMessage("客服已结束本次服务。");
        endMsg.setMsgType(employeeId.toString());
        endMsg.setEmployeeId(employeeId);
        endMsg.setIsRead(0);
        endMsg.setConfidence(BigDecimal.ONE);
        endMsg.setCreatedAt(LocalDateTime.now());
        chatRecordMapper.insert(endMsg);

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
    @PostMapping("/request-human")
    public ResponseUtil.Result<?> requestHumanService(@RequestBody Map<String, String> params) {
        String sessionId = params.get("sessionId");
        Long userId = UserContext.getCurrentUserId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return ResponseUtil.error(ResponseCode.PARAM_ERROR);
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

        log.info("用户 {} 请求转人工客服，会话ID: {}", userId, sessionId);
        return ResponseUtil.success("转人工请求已提交");
    }

    /**
     * 发送客服消息（供前端客服端调用，通过 REST 而非 WebSocket）
     */
    @PostMapping("/send-message")
    public ResponseUtil.Result<?> sendMessage(@RequestBody Map<String, Object> params) {
        Long employeeId = UserContext.getCurrentEmployeeId();
        if (employeeId == null) {
            return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
        }

        String sessionId = (String) params.get("sessionId");
        String content = (String) params.get("content");
        Long userId = (Long) params.get("userId");

        if (sessionId == null || content == null) {
            return ResponseUtil.error(ResponseCode.PARAM_ERROR);
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

        log.info("客服 {} 发送消息到会话 {}: {}", employeeId, sessionId, content);
        return ResponseUtil.success("消息发送成功");
    }
}
