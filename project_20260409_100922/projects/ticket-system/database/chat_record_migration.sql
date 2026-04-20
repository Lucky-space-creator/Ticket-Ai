-- ========================================
-- chat_record 表结构迁移脚本
-- 目的：复用 chat_record 表存储客服对话记录
-- 修改内容：
--   1. 将 msg_type 字段类型从 TINYINT 改为 VARCHAR(20)，支持存储 'user'、'robot' 或员工号
--   2. 新增 employee_id 字段，关联客服员工
--   3. 新增 is_read 字段，标记消息是否已读
--   4. 添加外键约束，确保 employee_id 引用 employee 表
--   5. 迁移现有数据，将数字类型的 msg_type 转换为字符串
-- ========================================

USE `ticket_system`;

-- 1. 修改 msg_type 字段类型
ALTER TABLE `chat_record`
MODIFY COLUMN `msg_type` VARCHAR(20) NOT NULL COMMENT '类型: user-用户, robot-机器人, 员工号-客服';

-- 2. 添加 employee_id 字段（允许 NULL，因为用户消息和机器人消息没有员工ID）
ALTER TABLE `chat_record`
ADD COLUMN `employee_id` BIGINT NULL COMMENT '客服员工ID（仅当 msg_type 为员工号时有效）' AFTER `msg_type`;

-- 3. 添加 is_read 字段（默认未读）
ALTER TABLE `chat_record`
ADD COLUMN `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读 0-未读 1-已读' AFTER `employee_id`;

-- 4. 添加外键约束（引用 employee 表）
ALTER TABLE `chat_record`
ADD CONSTRAINT `fk_chat_record_employee`
FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`)
ON DELETE SET NULL ON UPDATE CASCADE;

-- 5. 迁移现有数据：将数字 msg_type 转换为字符串
--    原 1 -> 'user'
--    原 2 -> 'robot'
UPDATE `chat_record`
SET `msg_type` = CASE `msg_type`
    WHEN '1' THEN 'user'
    WHEN '2' THEN 'robot'
    ELSE `msg_type`  -- 以防有其他值
END;

-- 6. 可选：更新注释以反映新的业务规则
--    （字段注释已在 MODIFY COLUMN 中更新，此处无需重复）

-- 7. 插入测试数据（用于验证新结构）
--    注意：以下插入语句假设存在员工 EMP001（id 需根据实际情况调整）和测试用户
--    您可以根据需要取消注释并执行

/*
-- 获取员工 EMP001 的 ID
SET @emp_id = (SELECT `id` FROM `employee` WHERE `employee_no` = 'EMP001' LIMIT 1);

-- 插入一条用户消息（msg_type = 'user'）
INSERT INTO `chat_record` (`user_id`, `session_id`, `message`, `msg_type`, `confidence`, `employee_id`, `is_read`)
VALUES (1, 'test_session_001', '你好，我想查询车票。', 'user', 0.95, NULL, 0);

-- 插入一条机器人回复（msg_type = 'robot'）
INSERT INTO `chat_record` (`user_id`, `session_id`, `message`, `msg_type`, `confidence`, `employee_id`, `is_read`)
VALUES (1, 'test_session_001', '您好，请问您要查询哪里的车票？', 'robot', 0.90, NULL, 0);

-- 插入一条客服回复（msg_type = 员工号）
INSERT INTO `chat_record` (`user_id`, `session_id`, `message`, `msg_type`, `confidence`, `employee_id`, `is_read`)
VALUES (1, 'test_session_001', '我可以帮您查询，请提供出发站和到达站。', 'EMP001', 0.98, @emp_id, 1);
*/

-- 8. 验证迁移结果
SELECT 
    'chat_record 表结构迁移完成' AS `message`,
    COUNT(*) AS `total_records`,
    SUM(CASE WHEN `msg_type` = 'user' THEN 1 ELSE 0 END) AS `user_messages`,
    SUM(CASE WHEN `msg_type` = 'robot' THEN 1 ELSE 0 END) AS `robot_messages`,
    SUM(CASE WHEN `msg_type` NOT IN ('user', 'robot') THEN 1 ELSE 0 END) AS `employee_messages`
FROM `chat_record`;

-- ========================================
-- 迁移完成
-- ========================================