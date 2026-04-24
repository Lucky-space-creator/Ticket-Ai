-- 简单的修复脚本，直接尝试添加列，如果列已存在会报错但可以忽略
-- 适用于 MySQL 5.7+，不依赖 IF NOT EXISTS 语法

-- 尝试添加 version 列（如果不存在）
ALTER TABLE `ticket_stock` 
ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' 
AFTER `available_seats`;

-- 尝试添加 updated_at 列（如果不存在）
ALTER TABLE `ticket_stock` 
ADD COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP 
AFTER `version`;

-- 显示表结构
DESCRIBE `ticket_stock`;