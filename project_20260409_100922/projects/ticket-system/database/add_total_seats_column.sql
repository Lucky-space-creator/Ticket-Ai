-- 添加 total_seats 列到 ticket_stock 表
-- 如果列不存在则添加，并设置默认值为 available_seats

SET @totalSeatsExists = 0;
SELECT COUNT(*) INTO @totalSeatsExists 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'ticket_stock' 
  AND COLUMN_NAME = 'total_seats';

SET @addTotalSeatsSQL = IF(@totalSeatsExists = 0, 
  'ALTER TABLE `ticket_stock` ADD COLUMN `total_seats` INT NOT NULL DEFAULT 0 COMMENT ''总座位数'' AFTER `price`;',
  'SELECT ''total_seats column already exists'' AS message;');
  
PREPARE stmt FROM @addTotalSeatsSQL;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 更新现有记录的 total_seats 为 available_seats（如果当前为0）
UPDATE `ticket_stock` SET `total_seats` = `available_seats` WHERE `total_seats` = 0 OR `total_seats` IS NULL;

-- 验证表结构
DESCRIBE `ticket_stock`;

-- 显示修复结果
SELECT 
  '添加 total_seats 列完成' AS status,
  CONCAT('total_seats 列: ', IF(@totalSeatsExists = 0, '已添加', '已存在')) AS column_status,
  CONCAT('更新记录数: ', ROW_COUNT()) AS updated_rows;