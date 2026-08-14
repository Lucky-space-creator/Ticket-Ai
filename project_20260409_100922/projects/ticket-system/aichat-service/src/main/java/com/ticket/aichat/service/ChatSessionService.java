package com.ticket.aichat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.entity.ChatSession;

import java.util.List;
import java.util.Map;

/**
 * 客服会话服务接口
 */
public interface ChatSessionService extends IService<ChatSession> {

    /**
     * 根据用户ID获取会话列表
     * @param userId 用户ID
     * @return 会话列表
     */
    List<ChatSession> getSessionsByUserId(Long userId);

    /**
     * 根据客服员工ID获取正在服务的会话列表
     * @param employeeId 客服员工ID
     * @return 会话列表
     */
    List<ChatSession> getSessionsByEmployeeId(Long employeeId);

    /**
     * 根据状态获取会话列表
     * @param status 会话状态
     * @return 会话列表
     */
    List<ChatSession> getSessionsByStatus(String status);

    /**
     * 获取待接入的会话列表（pending状态）
     * @return 会话列表
     */
    List<ChatSession> getPendingSessions();

    /**
     * 获取已结束的会话列表（ended状态）
     * @return 会话列表
     */
    List<ChatSession> getEndedSessions();

    /**
     * 创建新的会话
     * @param userId 用户ID（可为空）
     * @param title 会话标题（可为空）
     * @return 会话ID
     */
    String createSession(Long userId, String title);

    /**
     * 创建AI对话会话（无客服参与）
     * @param userId 用户ID（可为空）
     * @return 会话ID
     */
    String createAiOnlySession(Long userId);

    /**
     * 创建「用户请求转人工」的待接入会话（pending 状态）
     * @param userId 用户ID
     * @return 会话ID
     */
    String createPendingSession(Long userId);

    /**
     * 客服接入会话
     * @param sessionId 会话ID
     * @param employeeId 客服员工ID
     * @return 是否成功
     */
    boolean acceptSession(String sessionId, Long employeeId);

    /**
     * 结束会话
     * @param sessionId 会话ID
     * @param endedBy 结束者类型：user/employee
     * @param endedById 结束者ID（用户ID或员工ID）
     * @return 是否成功
     */
    boolean endSession(String sessionId, String endedBy, Long endedById);

    /**
     * 更新会话最后消息时间
     * @param sessionId 会话ID
     * @return 是否成功
     */
    boolean updateLastMessageTime(String sessionId);

    /**
     * 增加会话消息计数
     * @param sessionId 会话ID
     * @return 是否成功
     */
    boolean incrementMessageCount(String sessionId);

    /**
     * 获取会话统计信息
     * @return 统计信息映射
     */
    Map<String, Object> getSessionStats();

    /**
     * 获取或创建用户的最新非结束会话
     * @param userId 用户ID
     * @return 会话ID
     */
    String getOrCreateSession(Long userId);

    /**
     * 用于 AI 对话落库的会话（{@link ChatSession#STATUS_AI_ONLY}）；不存在则创建。
     */
    String getOrCreateAiOnlySessionId(Long userId);
}