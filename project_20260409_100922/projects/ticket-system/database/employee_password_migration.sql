-- employee表添加password字段迁移脚本
-- 如果已存在password字段，可以先删除（谨慎操作）
-- ALTER TABLE `employee` DROP COLUMN IF EXISTS `password`;

ALTER TABLE `employee` 
ADD COLUMN `password` VARCHAR(100) NOT NULL COMMENT '加密密码' 
DEFAULT '$2a$10$vI8aWBnW3fID.ZQ4/zo1G.q1lRps.9cGLcZEiGDMVr5yUP1KUOYTa'
AFTER `phone`;

-- 更新现有员工的密码（如果已有数据）
-- 默认密码为123456（已加密）
-- 如果需要自定义密码，请执行以下UPDATE语句：
-- UPDATE `employee` SET `password` = '$2a$10$vI8aWBnW3fID.ZQ4/zo1G.q1lRps.9cGLcZEiGDMVr5yUP1KUOYTa' WHERE `password` IS NULL OR `password` = '';

-- 注意：如果employee表有唯一键冲突，可能需要调整