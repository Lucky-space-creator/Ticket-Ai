package com.ticket.aichat.service;

/**
 * 用户画像服务：基于历史聊天记录生成用户偏好摘要，存入向量数据库。
 * <p>
 * 每个用户独立一份画像，支持增量更新和全量重建。
 */
public interface UserProfileService {

    /**
     * 获取用户画像文本（优先 Redis 缓存，其次 ChromaDB）
     *
     * @param userId 用户 ID
     * @return 画像摘要文本，无画像时返回空字符串
     */
    String getUserProfile(Long userId);

    /**
     * 异步生成/更新用户画像（会话结束或消息达到阈值时触发）
     *
     * @param userId    用户 ID
     * @param sessionId 当前会话 ID
     */
    void generateProfile(Long userId, String sessionId);

    /**
     * 删除用户画像（用户注销时调用）
     *
     * @param userId 用户 ID
     */
    void deleteProfile(Long userId);
}
