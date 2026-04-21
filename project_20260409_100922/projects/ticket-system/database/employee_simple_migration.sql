-- ========================================
-- employee 表结构修改与数据初始化脚本（简单版）
-- 目的：安全地修改 employee 表结构并初始化/更新数据
-- 特点：不使用存储过程，使用简单SQL语句
-- 注意：如果列已存在或外键已存在，可能会产生警告，可忽略
-- ========================================

USE `ticket_system`;

-- 1. 禁用外键检查（确保可以修改被引用的表）
SET FOREIGN_KEY_CHECKS = 0;

-- 2. 尝试删除外键约束（如果存在）
--    如果外键不存在，会显示警告，可忽略
# ALTER TABLE `chat_record` DROP FOREIGN KEY `fk_chat_record_employee`;

-- 3. 尝试添加 avatar 字段（如果不存在）
--    如果字段已存在，会显示警告，可忽略
ALTER TABLE `employee` 
# ADD COLUMN `avatar` VARCHAR(200) NULL COMMENT '头像URL',
ADD COLUMN `role_id` BIGINT DEFAULT NULL COMMENT '角色ID'
AFTER `remark`;

-- 4. 更新现有数据：为所有在职员工设置默认头像
UPDATE `employee` 
SET `avatar` = 'https://example.com/default-avatar.png'
WHERE `status` = 1 
  AND (`avatar` IS NULL OR `avatar` = '');

-- 5. 插入新的初始化数据：添加测试客服人员
--    避免重复插入，使用 NOT EXISTS 检查
INSERT INTO `employee` (
    `employee_no`, `name`, `gender`, `phone`, `password`, `email`,
    `department_id`, `position`, `hire_date`, `id_card`, `status`, `role_id`, `remark`, `avatar`
)
SELECT 
    'EMP004' AS `employee_no`,
    '赵客服' AS `name`,
    2 AS `gender`,  -- 女
    '13900139003' AS `phone`,
    '$2a$10$vI8aWBnW3fID.ZQ4/zo1G.q1lRps.9cGLcZEiGDMVr5yUP1KUOYTa' AS `password`,  -- 默认密码 123456
    'zhao@example.com' AS `email`,
    (SELECT `id` FROM `department` WHERE `dept_code` = 'OP' LIMIT 1) AS `department_id`,
    '客服专员' AS `position`,
    '2023-07-01' AS `hire_date`,
    '110101198304044567' AS `id_card`,
    1 AS `status`,  -- 在职
    (SELECT `id` FROM `role` WHERE `role_name` = 'operator' LIMIT 1) AS `role_id`,
    '新入职客服人员' AS `remark`,
    'https://example.com/avatar-zhao.png' AS `avatar`
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `employee` WHERE `employee_no` = 'EMP004'
);

-- 插入更多测试数据
INSERT INTO `employee` (
    `employee_no`, `name`, `gender`, `phone`, `password`, `email`,
    `department_id`, `position`, `hire_date`, `id_card`, `status`, `role_id`, `remark`, `avatar`
)
SELECT 
    'EMP005' AS `employee_no`,
    '钱技术' AS `name`,
    1 AS `gender`,  -- 男
    '13900139004' AS `phone`,
    '$2a$10$vI8aWBnW3fID.ZQ4/zo1G.q1lRps.9cGLcZEiGDMVr5yUP1KUOYTa' AS `password`,
    'qian@example.com' AS `email`,
    (SELECT `id` FROM `department` WHERE `dept_code` = 'IT' LIMIT 1) AS `department_id`,
    '开发工程师' AS `position`,
    '2023-08-15' AS `hire_date`,
    '110101198405055678' AS `id_card`,
    1 AS `status`,
    (SELECT `id` FROM `role` WHERE `role_name` = 'admin' LIMIT 1) AS `role_id`,
    '后端开发' AS `remark`,
    'https://example.com/avatar-qian.png' AS `avatar`
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `employee` WHERE `employee_no` = 'EMP005'
);

-- 6. 尝试重新添加外键约束
--    如果外键已存在，会显示警告，可忽略
ALTER TABLE `chat_record`
ADD CONSTRAINT `fk_chat_record_employee`
FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`)
ON DELETE SET NULL ON UPDATE CASCADE;

-- 7. 启用外键检查
SET FOREIGN_KEY_CHECKS = 1;

-- 8. 验证修改结果
SELECT 
    'employee 表修改与数据初始化完成' AS `message`,
    COUNT(*) AS `total_employees`,
    SUM(CASE WHEN `avatar` IS NOT NULL AND `avatar` != '' THEN 1 ELSE 0 END) AS `employees_with_avatar`,
    SUM(CASE WHEN `status` = 1 THEN 1 ELSE 0 END) AS `active_employees`
FROM `employee`;

-- ========================================
-- 脚本结束
-- ========================================