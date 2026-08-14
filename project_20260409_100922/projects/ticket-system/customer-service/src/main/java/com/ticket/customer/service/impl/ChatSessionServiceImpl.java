package com.ticket.customer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.customer.mapper.ChatSessionMapper;
import com.ticket.customer.service.ChatSessionService;
import com.ticket.entity.ChatSession;
import com.ticket.util.SnowflakeIdUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Override
    public List<ChatSession> getSessionsByUserId(Long userId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getLastMessageAt);
        return chatSessionMapper.selectList(wrapper);
    }

    @Override
    public List<ChatSession> getSessionsByEmployeeId(Long employeeId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getEmployeeId, employeeId)
                .orderByDesc(ChatSession::getLastMessageAt);
        return chatSessionMapper.selectList(wrapper);
    }

    @Override
    public List<ChatSession> getSessionsByStatus(String status) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getStatus, status)
                .orderByDesc(ChatSession::getLastMessageAt);
        return chatSessionMapper.selectList(wrapper);
    }

    @Override
    public List<ChatSession> getPendingSessions() {
        return getSessionsByStatus(ChatSession.STATUS_PENDING);
    }

    @Override
    public List<ChatSession> getEndedSessions() {
        return getSessionsByStatus(ChatSession.STATUS_ENDED);
    }

    @Override
    @Transactional
    public String createSession(Long userId, String title) {
        String sessionId = generateSessionId();
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus(ChatSession.STATUS_ACTIVE);
        session.setTitle(title);
        session.setMessageCount(0);
        session.setLastMessageAt(LocalDateTime.now());
        chatSessionMapper.insert(session);
        return sessionId;
    }

    @Override
    @Transactional
    public String createAiOnlySession(Long userId) {
        String sessionId = generateSessionId();
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setStatus(ChatSession.STATUS_AI_ONLY);
        session.setTitle("AI对话");
        session.setMessageCount(0);
        session.setLastMessageAt(LocalDateTime.now());
        chatSessionMapper.insert(session);
        return sessionId;
    }

    @Override
    @Transactional
    public boolean acceptSession(String sessionId, Long employeeId) {
        // 原子更新：仅当会话仍处于 pending 时才接单成功，避免并发双坐席抢接导致归属错乱。
        LambdaUpdateWrapper<ChatSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ChatSession::getId, sessionId)
                .eq(ChatSession::getStatus, ChatSession.STATUS_PENDING)
                .set(ChatSession::getEmployeeId, employeeId)
                .set(ChatSession::getStatus, ChatSession.STATUS_ACTIVE)
                .set(ChatSession::getUpdatedAt, LocalDateTime.now());
        return chatSessionMapper.update(updateWrapper) > 0;
    }

    @Override
    @Transactional
    public boolean endSession(String sessionId, String endedBy, Long endedById) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            return false;
        }
        if (ChatSession.STATUS_ENDED.equals(session.getStatus())) {
            return true;
        }
        session.setStatus(ChatSession.STATUS_ENDED);
        session.setUpdatedAt(LocalDateTime.now());
        return chatSessionMapper.updateById(session) > 0;
    }

    @Override
    @Transactional
    public boolean updateLastMessageTime(String sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            return false;
        }
        session.setLastMessageAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        return chatSessionMapper.updateById(session) > 0;
    }

    @Override
    @Transactional
    public boolean incrementMessageCount(String sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            return false;
        }
        session.setMessageCount(session.getMessageCount() + 1);
        session.setLastMessageAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        return chatSessionMapper.updateById(session) > 0;
    }

    @Override
    public Map<String, Object> getSessionStats() {
        Map<String, Object> stats = new HashMap<>();
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ChatSession::getStatus)
                .groupBy(ChatSession::getStatus);
        List<Map<String, Object>> statusCounts = chatSessionMapper.selectMaps(wrapper);
        for (Map<String, Object> row : statusCounts) {
            String status = (String) row.get("status");
            Long count = (Long) row.get("count");
            stats.put(status + "_count", count);
        }
        Long total = chatSessionMapper.selectCount(null);
        stats.put("total_count", total);
        LambdaQueryWrapper<ChatSession> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.ge(ChatSession::getCreatedAt, LocalDateTime.now().toLocalDate().atStartOfDay());
        Long todayCount = chatSessionMapper.selectCount(todayWrapper);
        stats.put("today_count", todayCount);
        return stats;
    }

    @Override
    @Transactional
    public String getOrCreateSession(Long userId) {
        if (userId == null) {
            return createAiOnlySession(null);
        }
        // 人工进行中 → 排队等待 → 仅 AI，避免把 AI 会话与人工会话混成一条导致 AI 消息进客服工作台
        LambdaQueryWrapper<ChatSession> active = new LambdaQueryWrapper<>();
        active.eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getStatus, ChatSession.STATUS_ACTIVE)
                .orderByDesc(ChatSession::getLastMessageAt)
                .last("LIMIT 1");
        ChatSession a = chatSessionMapper.selectOne(active);
        if (a != null) {
            return a.getId();
        }
        LambdaQueryWrapper<ChatSession> pending = new LambdaQueryWrapper<>();
        pending.eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getStatus, ChatSession.STATUS_PENDING)
                .orderByDesc(ChatSession::getLastMessageAt)
                .last("LIMIT 1");
        ChatSession p = chatSessionMapper.selectOne(pending);
        if (p != null) {
            return p.getId();
        }
        LambdaQueryWrapper<ChatSession> aiOnly = new LambdaQueryWrapper<>();
        aiOnly.eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getStatus, ChatSession.STATUS_AI_ONLY)
                .orderByDesc(ChatSession::getLastMessageAt)
                .last("LIMIT 1");
        ChatSession ai = chatSessionMapper.selectOne(aiOnly);
        if (ai != null) {
            return ai.getId();
        }
        return createAiOnlySession(userId);
    }

    private String generateSessionId() {
        return "user_" + SnowflakeIdUtil.getInstance().nextIdStr();
    }
}
