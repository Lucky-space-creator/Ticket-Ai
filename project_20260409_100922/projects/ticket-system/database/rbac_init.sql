-- ========================================
-- RBAC权限模块初始化SQL
-- 基于《RBAC数据库设计.md》文档
-- ========================================

-- 1. 修改用户表，添加角色ID字段
ALTER TABLE `user` ADD COLUMN `role_id` BIGINT DEFAULT NULL COMMENT '角色ID' AFTER `status`;

-- 2. 角色表
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
    `role_name` VARCHAR(50) NOT NULL COMMENT '角色名称（唯一标识）',
    `role_display_name` VARCHAR(50) NOT NULL COMMENT '角色显示名称',
    `description` VARCHAR(200) COMMENT '角色描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_role_name` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- 3. 权限表
DROP TABLE IF EXISTS `permission`;
CREATE TABLE `permission` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '权限ID',
    `permission_name` VARCHAR(100) NOT NULL COMMENT '权限名称（唯一标识）',
    `permission_display_name` VARCHAR(100) NOT NULL COMMENT '权限显示名称',
    `permission_type` TINYINT NOT NULL COMMENT '权限类型 1-菜单 2-按钮 3-API',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父权限ID',
    `path` VARCHAR(200) COMMENT '前端路由路径（菜单权限使用）',
    `component` VARCHAR(200) COMMENT '前端组件路径（菜单权限使用）',
    `icon` VARCHAR(50) COMMENT '菜单图标',
    `sort` INT DEFAULT 0 COMMENT '排序序号',
    `api_method` VARCHAR(10) COMMENT 'API请求方法（GET/POST/PUT/DELETE）',
    `api_path` VARCHAR(200) COMMENT 'API路径（API权限使用）',
    `description` VARCHAR(200) COMMENT '权限描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_permission_name` (`permission_name`),
    INDEX `idx_parent_id` (`parent_id`),
    INDEX `idx_permission_type` (`permission_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- 4. 角色权限关联表
DROP TABLE IF EXISTS `role_permission`;
CREATE TABLE `role_permission` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    INDEX `idx_role_id` (`role_id`),
    INDEX `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- ========================================
-- 初始化数据
-- ========================================

-- 插入初始角色数据
INSERT INTO `role` (`role_name`, `role_display_name`, `description`, `status`) VALUES
('super_admin', '超级管理员', '拥有系统所有权限，可以管理用户、角色、权限等', 1),
('admin', '管理员', '拥有大部分管理权限，但无法管理系统用户和角色', 1),
('operator', '操作员', '拥有基础操作权限，如查看订单、处理退票等', 1);

-- 插入初始权限数据
-- 系统管理模块
INSERT INTO `permission` (`permission_name`, `permission_display_name`, `permission_type`, `parent_id`, `path`, `component`, `icon`, `sort`, `api_method`, `api_path`, `description`, `status`) VALUES
('system', '系统管理', 1, 0, '/system', 'Layout', 'setting', 100, NULL, NULL, '系统管理菜单', 1),
('system:user', '用户管理', 1, 1, '/system/users', 'system/user/index', 'user', 101, NULL, NULL, '用户管理菜单', 1),
('system:user:list', '用户列表', 2, 2, NULL, NULL, NULL, 0, 'GET', '/api/admin/users', '获取用户列表', 1),
('system:user:status', '更新用户状态', 2, 2, NULL, NULL, NULL, 0, 'PUT', '/api/admin/users/{id}/status', '更新用户状态', 1),
('system:user:add', '添加用户', 2, 2, NULL, NULL, NULL, 0, 'POST', '/api/admin/users', '添加用户接口', 1),
('system:role', '角色管理', 1, 1, '/system/roles', 'system/role/index', 'role', 102, NULL, NULL, '角色管理菜单', 1),
('system:role:list', '角色列表', 2, 6, NULL, NULL, NULL, 0, 'GET', '/api/admin/roles', '获取角色列表', 1),
('system:role:add', '添加角色', 2, 6, NULL, NULL, NULL, 0, 'POST', '/api/admin/roles', '添加角色', 1),
('system:role:edit', '编辑角色', 2, 6, NULL, NULL, NULL, 0, 'PUT', '/api/admin/roles/{id}', '编辑角色', 1),
('system:role:delete', '删除角色', 2, 6, NULL, NULL, NULL, 0, 'DELETE', '/api/admin/roles/{id}', '删除角色', 1),
('system:role:assign', '权限分配', 2, 6, NULL, NULL, NULL, 0, 'POST', '/api/admin/roles/{id}/permissions', '为角色分配权限', 1),
('system:permission', '权限管理', 1, 1, '/system/permissions', 'system/permission/index', 'permission', 103, NULL, NULL, '权限管理菜单', 1),
('system:permission:list', '权限列表', 2, 12, NULL, NULL, NULL, 0, 'GET', '/api/admin/permissions', '获取权限列表', 1),
('system:permission:add', '添加权限', 2, 12, NULL, NULL, NULL, 0, 'POST', '/api/admin/permissions', '添加权限', 1),
('system:permission:edit', '编辑权限', 2, 12, NULL, NULL, NULL, 0, 'PUT', '/api/admin/permissions/{id}', '编辑权限', 1),
('system:permission:delete', '删除权限', 2, 12, NULL, NULL, NULL, 0, 'DELETE', '/api/admin/permissions/{id}', '删除权限', 1),
('system:log', '操作日志', 1, 1, '/system/logs', 'system/log/index', 'log', 104, NULL, NULL, '操作日志菜单', 1);

-- 车次管理模块
INSERT INTO `permission` (`permission_name`, `permission_display_name`, `permission_type`, `parent_id`, `path`, `component`, `icon`, `sort`, `api_method`, `api_path`, `description`, `status`) VALUES
('train', '车次管理', 1, 0, '/train', 'Layout', 'train', 200, NULL, NULL, '车次管理菜单', 1),
('train:list', '车次列表', 1, 18, '/train/list', 'train/list/index', 'list', 201, NULL, NULL, '车次列表菜单', 1),
('train:list:api', '车次列表API', 3, 19, NULL, NULL, NULL, 0, 'GET', '/api/admin/trains', '获取车次列表接口', 1),
('train:add', '添加车次', 2, 19, NULL, NULL, NULL, 0, 'POST', '/api/admin/trains', '添加车次接口', 1),
('train:edit', '编辑车次', 2, 19, NULL, NULL, NULL, 0, 'PUT', '/api/admin/trains/{id}', '编辑车次接口', 1),
('train:status', '更新车次状态', 2, 19, NULL, NULL, NULL, 0, 'PUT', '/api/admin/trains/{id}/status', '更新车次状态接口', 1),
('train:stock', '设置余票', 2, 19, NULL, NULL, NULL, 0, 'POST', '/api/admin/trains/{id}/stock', '设置余票接口', 1),
('train:station', '车站管理', 1, 18, '/train/stations', 'train/station/index', 'station', 202, NULL, NULL, '车站管理菜单', 1);

-- 订单管理模块
INSERT INTO `permission` (`permission_name`, `permission_display_name`, `permission_type`, `parent_id`, `path`, `component`, `icon`, `sort`, `api_method`, `api_path`, `description`, `status`) VALUES
('order', '订单管理', 1, 0, '/order', 'Layout', 'order', 300, NULL, NULL, '订单管理菜单', 1),
('order:list', '订单列表', 1, 27, '/order/list', 'order/list/index', 'list', 301, NULL, NULL, '订单列表菜单', 1),
('order:list:api', '订单列表API', 3, 28, NULL, NULL, NULL, 0, 'GET', '/api/admin/orders', '获取订单列表接口', 1),
('order:refund', '退票操作', 2, 28, NULL, NULL, NULL, 0, 'POST', '/api/admin/orders/{orderNo}/refund', '退票接口', 1),
('order:stats', '订单统计', 1, 27, '/order/stats', 'order/stats/index', 'stats', 302, NULL, NULL, '订单统计菜单', 1);

-- 知识库管理模块
INSERT INTO `permission` (`permission_name`, `permission_display_name`, `permission_type`, `parent_id`, `path`, `component`, `icon`, `sort`, `api_method`, `api_path`, `description`, `status`) VALUES
('knowledge', '知识库管理', 1, 0, '/knowledge', 'Layout', 'knowledge', 400, NULL, NULL, '知识库管理菜单', 1),
('knowledge:list', '知识列表', 1, 32, '/knowledge/list', 'knowledge/list/index', 'list', 401, NULL, NULL, '知识列表菜单', 1),
('knowledge:list:api', '知识列表API', 3, 33, NULL, NULL, NULL, 0, 'GET', '/api/knowledge/list', '获取知识库列表接口', 1),
('knowledge:add', '添加知识', 2, 33, NULL, NULL, NULL, 0, 'POST', '/api/knowledge/add', '添加知识接口', 1),
('knowledge:edit', '编辑知识', 2, 33, NULL, NULL, NULL, 0, 'PUT', '/api/knowledge/update', '更新知识接口', 1),
('knowledge:delete', '删除知识', 2, 33, NULL, NULL, NULL, 0, 'DELETE', '/api/knowledge/{id}', '删除知识接口', 1);

-- 智能客服模块
INSERT INTO `permission` (`permission_name`, `permission_display_name`, `permission_type`, `parent_id`, `path`, `component`, `icon`, `sort`, `api_method`, `api_path`, `description`, `status`) VALUES
('ai', '智能客服', 1, 0, '/ai', 'Layout', 'chat', 500, NULL, NULL, '智能客服菜单', 1),
('ai:chat', '对话记录', 1, 39, '/ai/chat', 'ai/chat/index', 'chat', 501, NULL, NULL, '对话记录菜单', 1),
('ai:chat:api', '对话记录API', 3, 40, NULL, NULL, NULL, 0, 'GET', '/api/chat/records', '获取对话记录接口', 1);

-- 统计管理模块（API权限）
INSERT INTO `permission` (`permission_name`, `permission_display_name`, `permission_type`, `parent_id`, `path`, `component`, `icon`, `sort`, `api_method`, `api_path`, `description`, `status`) VALUES
('stats:overview:api', '数据概览API', 3, 0, NULL, NULL, NULL, 0, 'GET', '/api/admin/stats/overview', '获取数据概览接口', 1),
('stats:trend:api', '趋势分析API', 3, 0, NULL, NULL, NULL, 0, 'GET', '/api/admin/stats/trend/{type}', '获取趋势分析接口', 1);

-- ========================================
-- 角色权限分配
-- ========================================

-- 为超级管理员分配所有权限
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p WHERE r.role_name = 'super_admin';

-- 为管理员分配除系统管理外的所有权限（即排除系统管理模块）
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p 
WHERE r.role_name = 'admin' 
  AND p.permission_name NOT LIKE 'system%' 
  AND p.permission_name NOT IN ('system');

-- 为操作员分配基础权限：查看车次列表、查看订单列表、查看知识库列表、查看对话记录
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p 
WHERE r.role_name = 'operator' 
  AND p.permission_name IN (
    'train:list:api', 
    'order:list:api', 
    'knowledge:list:api',
    'ai:chat:api'
  );

-- ========================================
-- 更新测试用户的角色（示例）
-- ========================================
-- 注意：假设测试用户已存在，这里仅作示例
UPDATE `user` SET `role_id` = (SELECT `id` FROM `role` WHERE `role_name` = 'super_admin') WHERE `phone` = '13800138000';
UPDATE `user` SET `role_id` = (SELECT `id` FROM `role` WHERE `role_name` = 'admin') WHERE `phone` = '13800138001';