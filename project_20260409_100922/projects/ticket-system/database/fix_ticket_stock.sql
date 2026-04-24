-- 修复 ticket_stock 表结构，添加缺失的 version 和 updated_at 列
-- 使用兼容性更好的方法，避免 IF NOT EXISTS 语法问题

-- 首先检查 version 列是否存在
SET @versionExists = 0;
SELECT COUNT(*) INTO @versionExists 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'ticket_stock' 
  AND COLUMN_NAME = 'version';

-- 如果 version 列不存在，则添加
SET @addVersionSQL = IF(@versionExists = 0, 
  'ALTER TABLE `ticket_stock` ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号'' AFTER `available_seats`;',
  'SELECT ''version column already exists'' AS message;');
  
PREPARE stmt FROM @addVersionSQL;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 检查 updated_at 列是否存在
SET @updatedAtExists = 0;
SELECT COUNT(*) INTO @updatedAtExists 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME = 'ticket_stock' 
  AND COLUMN_NAME = 'updated_at';

-- 如果 updated_at 列不存在，则添加
SET @addUpdatedAtSQL = IF(@updatedAtExists = 0, 
  'ALTER TABLE `ticket_stock` ADD COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `version`;',
  'SELECT ''updated_at column already exists'' AS message;');
  
PREPARE stmt2 FROM @addUpdatedAtSQL;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 验证表结构
DESCRIBE `ticket_stock`;

-- 显示修复结果
SELECT 
  '修复完成' AS status,
  CONCAT('version 列: ', IF(@versionExists = 0, '已添加', '已存在')) AS version_status,
  CONCAT('updated_at 列: ', IF(@updatedAtExists = 0, '已添加', '已存在')) AS updated_at_status;