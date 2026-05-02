package com.ticket.customer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            return false;
        }
        if (!ChatSession.STATUS_PENDING.equals(session.getStatus())) {
            return false;
        }
        session.setEmployeeId(employeeId);
        session.setStatus(ChatSession.STATUS_ACTIVE);
        session.setUpdatedAt(LocalDateTime.now());
        return chatSessionMapper.updateById(session) > 0;
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
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId)
                .ne(ChatSession::getStatus, ChatSession.STATUS_ENDED)
                .orderByDesc(ChatSession::getLastMessageAt)
                .last("LIMIT 1");
        ChatSession session = chatSessionMapper.selectOne(wrapper);
        if (session != null) {
            return session.getId();
        }
        return createAiOnlySession(userId);
    }

    private String generateSessionId() {
        return "user_" + SnowflakeIdUtil.getInstance().nextIdStr();
    }
}
