-- 用户聊天画像表
CREATE TABLE IF NOT EXISTS user_chat_profile (
    id BIGINT NOT NULL COMMENT '主键（雪花ID）',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    summary TEXT NOT NULL COMMENT '画像摘要文本',
    message_count INT DEFAULT 0 COMMENT '总结时的消息条数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户聊天画像';
