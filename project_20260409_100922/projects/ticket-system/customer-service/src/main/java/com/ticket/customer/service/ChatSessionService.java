package com.ticket.customer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.entity.ChatSession;

import java.util.List;
import java.util.Map;

public interface ChatSessionService extends IService<ChatSession> {

    List<ChatSession> getSessionsByUserId(Long userId);

    List<ChatSession> getSessionsByEmployeeId(Long employeeId);

    List<ChatSession> getSessionsByStatus(String status);

    List<ChatSession> getPendingSessions();

    List<ChatSession> getEndedSessions();

    String createSession(Long userId, String title);

    String createAiOnlySession(Long userId);

    boolean acceptSession(String sessionId, Long employeeId);

    boolean endSession(String sessionId, String endedBy, Long endedById);

    boolean updateLastMessageTime(String sessionId);

    boolean incrementMessageCount(String sessionId);

    Map<String, Object> getSessionStats();

    String getOrCreateSession(Long userId);
}
