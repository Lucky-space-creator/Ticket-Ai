-- ========================================
-- chat_record 表添加 Token 消耗字段
-- 目的：记录每次AI调用的Token消耗，用于成本监控和用户用量控制
-- 修改内容：
--   1. 新增 input_tokens 字段（AI输入Token数）
--   2. 新增 output_tokens 字段（AI输出Token数）
--   3. 新增联合索引（支持按用户统计token消耗）
-- ========================================

USE `ticket_system`;

-- 1. 添加 AI 输入 Token 字段
ALTER TABLE `chat_record`
ADD COLUMN `input_tokens` INT NOT NULL DEFAULT 0 COMMENT 'AI输入Token数（仅AI回复消息有效，用户消息为0）' AFTER `confidence`;

-- 2. 添加 AI 输出 Token 字段
ALTER TABLE `chat_record`
ADD COLUMN `output_tokens` INT NOT NULL DEFAULT 0 COMMENT 'AI输出Token数（仅AI回复消息有效，用户消息为0）' AFTER `input_tokens`;

-- 3. 添加用户+token联合索引（支持按用户统计token消耗查询）
CREATE INDEX `idx_user_token` ON `chat_record` (`user_id`, `output_tokens`);

-- 4. 验证迁移结果
SELECT 
    'chat_record token字段迁移完成' AS message,
    COLUMN_NAME,
    COLUMN_TYPE,
    COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'ticket_system'
    AND TABLE_NAME = 'chat_record'
    AND COLUMN_NAME IN ('input_tokens', 'output_tokens');

-- ========================================
-- 迁移完成
-- 后续使用说明：
--   1. 用户消息: input_tokens=0, output_tokens=0
--   2. AI回复消息(同步): input_tokens=实际值, output_tokens=实际值
--   3. AI回复消息(流式): input_tokens=实际值, output_tokens=实际值
--   4. 转人工/系统消息: input_tokens=0, output_tokens=0
--
-- 按用户查询token消耗示例:
--   SELECT user_id, 
--          SUM(input_tokens) AS total_input,
--          SUM(output_tokens) AS total_output,
--          COUNT(*) AS chat_count
--     FROM chat_record 
--    WHERE msg_type = 'robot' 
--      AND created_at >= CURDATE() - INTERVAL 7 DAY
--    GROUP BY user_id;
-- ========================================
