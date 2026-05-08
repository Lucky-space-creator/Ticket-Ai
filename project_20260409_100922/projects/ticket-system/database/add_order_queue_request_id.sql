-- 订单表：异步排队幂等键（与 order-queue requestId 对齐）
SET @db := DATABASE();

SET @has_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'order' AND COLUMN_NAME = 'queue_request_id');

SET @sql := IF(@has_col = 0,
    'ALTER TABLE `order` ADD COLUMN `queue_request_id` VARCHAR(64) NULL COMMENT ''MQ异步排队requestId幂等键'' AFTER `route_type`;',
    'SELECT ''order.queue_request_id 已存在，跳过'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_uk := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'order' AND INDEX_NAME = 'uk_order_queue_request_id');

SET @sql2 := IF(@has_uk = 0,
    'ALTER TABLE `order` ADD UNIQUE KEY `uk_order_queue_request_id` (`queue_request_id`);',
    'SELECT ''uk_order_queue_request_id 已存在，跳过'' AS info');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
