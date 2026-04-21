-- ========================================
-- chat_session 表结构迁移脚本
-- 目的：新增会话表，记录每一次会话，优化聊天记录表结构
-- 修改内容：
--   1. 创建 chat_session 表
--   2. 迁移现有聊天记录中的会话数据到 chat_session 表
--   3. 优化 chat_record 表结构，添加外键约束
--   4. 生成缺失的会话记录（session_id 为空的记录）
-- ========================================

USE `ticket_system`;

-- 禁用外键检查，避免迁移过程中约束冲突
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------
-- 1. 创建 chat_session 表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `chat_session`;
CREATE TABLE `chat_session` (
    `id` VARCHAR(64) PRIMARY KEY COMMENT '会话ID（与 chat_record.session_id 对应）',
    `user_id` BIGINT NULL COMMENT '用户ID（未登录为NULL）',
    `employee_id` BIGINT NULL COMMENT '当前接待客服员工ID',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '会话状态: active-进行中, pending-等待接入, ended-已结束, ai_only-仅AI对话',
    `title` VARCHAR(200) NULL COMMENT '会话标题/摘要',
    `message_count` INT NOT NULL DEFAULT 0 COMMENT '消息条数',
    `last_message_at` DATETIME NULL COMMENT '最后消息时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_employee_id` (`employee_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_last_message_at` (`last_message_at`),
    CONSTRAINT `fk_chat_session_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `fk_chat_session_employee` FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服会话表';

-- -----------------------------------------------------
-- 2. 迁移现有聊天记录中的会话数据到 chat_session 表
-- -----------------------------------------------------
-- 2.1 处理非空 session_id：按 session_id 分组，提取最新信息
INSERT INTO `chat_session` (`id`, `user_id`, `employee_id`, `status`, `message_count`, `last_message_at`, `created_at`, `updated_at`)
SELECT 
    cr.session_id AS id,
    MAX(cr.user_id) AS user_id,
    MAX(cr.employee_id) AS employee_id,
    CASE 
        -- 如果存在 pending 消息，状态为 pending
        WHEN EXISTS (SELECT 1 FROM chat_record WHERE session_id = cr.session_id AND msg_type = 'pending') THEN 'pending'
        -- 如果存在 ended 消息，状态为 ended
        WHEN EXISTS (SELECT 1 FROM chat_record WHERE session_id = cr.session_id AND msg_type = 'ended') THEN 'ended'
        -- 如果存在员工号消息（客服消息），状态为 active
        WHEN EXISTS (SELECT 1 FROM chat_record WHERE session_id = cr.session_id AND msg_type NOT IN ('user', 'robot', 'pending', 'ended')) THEN 'active'
        -- 否则为 ai_only（只有用户和机器人消息）
        ELSE 'ai_only'
    END AS status,
    COUNT(*) AS message_count,
    MAX(cr.created_at) AS last_message_at,
    MIN(cr.created_at) AS created_at,
    MAX(cr.created_at) AS updated_at
FROM `chat_record` cr
WHERE cr.session_id IS NOT NULL AND cr.session_id != ''
GROUP BY cr.session_id
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    employee_id = VALUES(employee_id),
    status = VALUES(status),
    message_count = VALUES(message_count),
    last_message_at = VALUES(last_message_at),
    updated_at = VALUES(updated_at);

-- 2.2 处理 session_id 为空的记录：为每一条空 session_id 记录生成一个新的会话
-- 首先，找出所有 session_id 为空的记录
DROP TEMPORARY TABLE IF EXISTS `temp_empty_sessions`;
CREATE TEMPORARY TABLE `temp_empty_sessions` (
    `record_id` BIGINT PRIMARY KEY,
    `user_id` BIGINT NULL,
    `employee_id` BIGINT NULL,
    `msg_type` VARCHAR(20),
    `created_at` DATETIME
);

INSERT INTO `temp_empty_sessions` (`record_id`, `user_id`, `employee_id`, `msg_type`, `created_at`)
SELECT 
    id,
    user_id,
    employee_id,
    msg_type,
    created_at
FROM `chat_record`
WHERE session_id IS NULL OR session_id = '';

-- 为每一条记录生成唯一的 session_id（使用UUID）
UPDATE `temp_empty_sessions`
SET record_id = record_id; -- 无实际作用，只是让临时表有数据

-- 生成会话记录，每个空 session 记录对应一个独立的会话
INSERT INTO `chat_session` (`id`, `user_id`, `employee_id`, `status`, `message_count`, `last_message_at`, `created_at`, `updated_at`)
SELECT 
    CONCAT('ai_', UUID_SHORT()) AS id,
    user_id,
    employee_id,
    'ai_only' AS status,
    1 AS message_count,
    created_at AS last_message_at,
    created_at AS created_at,
    created_at AS updated_at
FROM `temp_empty_sessions`
GROUP BY record_id;

-- 更新 chat_record 表，将空 session_id 设置为新生成的会话ID
UPDATE `chat_record` cr
INNER JOIN `temp_empty_sessions` tes ON cr.id = tes.record_id
INNER JOIN `chat_session` cs ON cs.user_id = tes.user_id 
    AND cs.created_at = tes.created_at 
    AND cs.status = 'ai_only'
    AND cs.message_count = 1
SET cr.session_id = cs.id
WHERE cr.session_id IS NULL OR cr.session_id = '';

DROP TEMPORARY TABLE `temp_empty_sessions`;

-- -----------------------------------------------------
-- 3. 优化 chat_record 表结构
-- -----------------------------------------------------
-- 3.1 添加外键约束（引用 chat_session 表）
ALTER TABLE `chat_record`
ADD CONSTRAINT `fk_chat_record_session`
FOREIGN KEY (`session_id`) REFERENCES `chat_session` (`id`)
ON DELETE CASCADE ON UPDATE CASCADE;

-- 3.2 添加索引优化查询性能
ALTER TABLE `chat_record`
ADD INDEX `idx_created_at` (`created_at`),
ADD INDEX `idx_msg_type` (`msg_type`),
ADD INDEX `idx_is_read` (`is_read`);

-- 3.3 可选：调整字段注释
ALTER TABLE `chat_record`
MODIFY COLUMN `session_id` VARCHAR(64) NOT NULL COMMENT '会话ID（引用 chat_session.id）',
MODIFY COLUMN `msg_type` VARCHAR(20) NOT NULL COMMENT '类型: user-用户, robot-机器人, pending-等待接入, ended-已结束, 员工号-客服消息',
MODIFY COLUMN `confidence` DECIMAL(3,2) DEFAULT NULL COMMENT '置信度（0.00-1.00）';

-- -----------------------------------------------------
-- 4. 验证迁移结果
-- -----------------------------------------------------
SELECT 'chat_session 表记录数' AS metric, COUNT(*) AS value FROM `chat_session`
UNION ALL
SELECT 'chat_record 表记录数', COUNT(*) FROM `chat_record`
UNION ALL
SELECT 'chat_record 中 session_id 为空的记录数', COUNT(*) FROM `chat_record` WHERE session_id IS NULL OR session_id = ''
UNION ALL
SELECT 'chat_session 状态分布', 0 FROM DUAL WHERE 0=1; -- 占位，下面用具体查询

-- 显示会话状态分布
SELECT 
    status,
    COUNT(*) AS session_count,
    SUM(message_count) AS total_messages
FROM `chat_session`
GROUP BY status
ORDER BY session_count DESC;

-- 显示各表结构
SHOW CREATE TABLE `chat_session`;
SHOW CREATE TABLE `chat_record`;

-- 启用外键检查
SET FOREIGN_KEY_CHECKS = 1;

-- ========================================
-- 迁移完成
-- ========================================