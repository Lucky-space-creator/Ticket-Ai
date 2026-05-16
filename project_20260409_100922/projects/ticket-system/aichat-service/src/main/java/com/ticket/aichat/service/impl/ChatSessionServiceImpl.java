package com.ticket.aichat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.entity.ChatSession;
import com.ticket.aichat.mapper.ChatSessionMapper;
import com.ticket.aichat.service.ChatSessionService;
import com.ticket.aichat.service.UserProfileService;
import com.ticket.util.SnowflakeIdUtil;
import com.ticket.util.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 客服会话服务实现类
 */
@Slf4j
@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Resource
    private UserProfileService userProfileService;

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
            // 如果不是pending状态，可能已经被其他客服接入
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
        boolean updated = chatSessionMapper.updateById(session) > 0;

        // 会话结束时异步触发用户画像生成
        if (updated && session.getUserId() != null) {
            log.info("会话结束，触发用户画像生成: userId={}, sessionId={}", session.getUserId(), sessionId);
            userProfileService.generateProfile(session.getUserId(), sessionId);
        }

        return updated;
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
        
        // 统计各状态会话数量
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ChatSession::getStatus)
                .groupBy(ChatSession::getStatus);
        List<Map<String, Object>> statusCounts = chatSessionMapper.selectMaps(wrapper);
        
        for (Map<String, Object> row : statusCounts) {
            String status = (String) row.get("status");
            Long count = (Long) row.get("count");
            stats.put(status + "_count", count);
        }
        
        // 总会话数
        Long total = chatSessionMapper.selectCount(null);
        stats.put("total_count", total);
        
        // 今日新增会话数（简化：按创建时间统计）
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

    @Override
    public String getOrCreateAiOnlySessionId(Long userId) {
        if (userId == null) {
            return createAiOnlySession(null);
        }
        LambdaQueryWrapper<ChatSession> w = new LambdaQueryWrapper<>();
        w.eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getStatus, ChatSession.STATUS_AI_ONLY)
                .orderByDesc(ChatSession::getLastMessageAt)
                .last("LIMIT 1");
        ChatSession s = chatSessionMapper.selectOne(w);
        if (s != null) {
            return s.getId();
        }
        return createAiOnlySession(userId);
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return "user_" + SnowflakeIdUtil.getInstance().nextIdStr();
    }
}