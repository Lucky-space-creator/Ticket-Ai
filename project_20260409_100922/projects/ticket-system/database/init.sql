-- ========================================
-- 12306购票系统数据库建表SQL
-- ========================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `ticket_system` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `ticket_system`;

-- 禁用外键检查（确保建表顺序不受外键依赖影响）
SET FOREIGN_KEY_CHECKS = 0;

-- ========================================
-- 1. 用户模块
-- ========================================

-- 用户表（合并实名信息，简化字段）
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号（登录账号）',
    `password` VARCHAR(100) NOT NULL COMMENT '加密密码',
    `real_name` VARCHAR(50) COMMENT '真实姓名',
    `id_card` VARCHAR(100) COMMENT '身份证号（加密存储）',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0-禁用 1-正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 常用联系人表（简化乘客类型）
DROP TABLE IF EXISTS `passenger`;
CREATE TABLE `passenger` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `name` VARCHAR(50) NOT NULL COMMENT '姓名',
    `id_card` VARCHAR(100) NOT NULL COMMENT '身份证号（加密）',
    `phone` VARCHAR(20) COMMENT '手机号',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='常用联系人表';

-- ========================================
-- 2. 车次模块（线段化 + 站点主数据 + 停靠序）
-- ========================================

DROP TABLE IF EXISTS `train_station`;
DROP TABLE IF EXISTS `train_route_stop`;
DROP TABLE IF EXISTS `station`;
CREATE TABLE `station` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `name` VARCHAR(50) NOT NULL COMMENT '站名（与线段/停靠表一致）',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_station_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站点主数据';

-- 按车次号的全程停靠模板（与同车线段校验共用）
CREATE TABLE `train_route_stop` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `train_no` VARCHAR(20) NOT NULL COMMENT '车次号',
    `station_name` VARCHAR(50) NOT NULL COMMENT '站点名称',
    `station_no` INT NOT NULL COMMENT '站序(第几站)',
    `arrive_time` TIME NULL COMMENT '到达时间',
    `depart_time` TIME NULL COMMENT '出发时间',
    UNIQUE KEY `uk_train_stop` (`train_no`, `station_no`),
    INDEX `idx_train_no` (`train_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车次停靠站时刻表';

-- 一行 = 一段可售卖 OD；id 为线段主键，用于 SKU / ticket_stock.train_id / Redis
DROP TABLE IF EXISTS `train`;
CREATE TABLE `train` (
    `id` BIGINT PRIMARY KEY COMMENT '线段ID（种子手写，与运行态 ASSIGN_ID 并存）',
    `train_no` VARCHAR(20) NOT NULL COMMENT '车次号 如G101',
    `train_type` TINYINT NOT NULL COMMENT '类型 1-高铁 2-动车 3-普快',
    `start_station` VARCHAR(50) NOT NULL COMMENT '本段起点站',
    `end_station` VARCHAR(50) NOT NULL COMMENT '本段终点站',
    `start_time` TIME NOT NULL COMMENT '本段发车时间',
    `end_time` TIME NOT NULL COMMENT '本段到达时间',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0-停运 1-正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_train_segment` (`train_no`, `start_station`, `end_station`),
    INDEX `idx_train_no` (`train_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车次线段表（可售OD）';

-- 余票库存表：train_id = 线段 id；sale_enabled 管理端停售开关（非 deduct）
DROP TABLE IF EXISTS `ticket_stock`;
CREATE TABLE `ticket_stock` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `train_id` BIGINT NOT NULL COMMENT '线段ID',
    `train_date` DATE NOT NULL COMMENT '乘车日期',
    `start_station` VARCHAR(50) NOT NULL COMMENT '出发站',
    `end_station` VARCHAR(50) NOT NULL COMMENT '到达站',
    `seat_type` TINYINT NOT NULL COMMENT '席别 1-商务 2-一等 3-二等 4-软卧 5-硬卧 6-硬座',
    `price` DECIMAL(10,2) NOT NULL COMMENT '票价',
    `total_seats` INT NOT NULL DEFAULT 0 COMMENT '总座位数',
    `available_seats` INT NOT NULL DEFAULT 0 COMMENT '剩余座位数',
    `sale_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许售票 0-停售 1-开售',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_stock` (`train_id`, `train_date`, `start_station`, `end_station`, `seat_type`),
    INDEX `idx_query` (`train_date`, `start_station`, `end_station`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='余票库存表';

-- ========================================
-- 3. 订单模块
-- ========================================

DROP TABLE IF EXISTS `order_route_leg`;
DROP TABLE IF EXISTS `order`;
CREATE TABLE `order` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `train_id` BIGINT NULL COMMENT '兼容/展示：首段线段ID，非业务真源',
    `train_no` VARCHAR(20) NULL COMMENT '兼容/展示：首段车次号',
    `route_sku` VARCHAR(256) NULL COMMENT '线段id按序拼接 如 9000001-9000002',
    `route_type` VARCHAR(20) NULL COMMENT 'DIRECT 同车联程 / TRANSFER 换乘 / SINGLE 单段',
    `queue_request_id` VARCHAR(64) NULL COMMENT 'MQ异步排队requestId幂等键',
    `train_date` DATE NOT NULL COMMENT '乘车日期',
    `start_station` VARCHAR(50) NOT NULL COMMENT '外层出发站',
    `end_station` VARCHAR(50) NOT NULL COMMENT '外层到达站',
    `depart_time` DATETIME NOT NULL COMMENT '首段发车时间（展示）',
    `seat_type` TINYINT NOT NULL COMMENT '席别',
    `total_amount` DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0-待支付 1-已支付 2-已退票 3-已取消',
    `pay_time` DATETIME COMMENT '支付时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_order_queue_request_id` (`queue_request_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单主表';

CREATE TABLE `order_route_leg` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `leg_seq` INT NOT NULL COMMENT '段序号 从1起',
    `segment_train_id` BIGINT NOT NULL COMMENT '线段表主键',
    `train_no` VARCHAR(20) NOT NULL COMMENT '车次号冗余',
    `from_station` VARCHAR(50) NOT NULL COMMENT '本段起点',
    `to_station` VARCHAR(50) NOT NULL COMMENT '本段终点',
    `segment_price` DECIMAL(10,2) NOT NULL COMMENT '本段票价',
    `planned_depart_at` DATETIME NULL COMMENT '计划发车',
    `planned_arrive_at` DATETIME NULL COMMENT '计划到达',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_order_id` (`order_id`),
    INDEX `idx_segment_train` (`segment_train_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单行程段（业务真源）';

CREATE TABLE `order_item` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `passenger_name` VARCHAR(50) NOT NULL COMMENT '乘客姓名',
    `id_card` VARCHAR(100) NOT NULL COMMENT '身份证号（加密）',
    `price` DECIMAL(10,2) NOT NULL COMMENT '单张票价',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单乘客明细表';

-- ========================================
-- 4. AI客服模块（核心保留）
-- ========================================

-- 客服知识库表（保留全文检索，简化分类）
DROP TABLE IF EXISTS `knowledge_base`;
CREATE TABLE `knowledge_base` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `category` VARCHAR(50) DEFAULT 'general' COMMENT '分类 如booking/refund/query/common',
    `question` VARCHAR(500) NOT NULL COMMENT '问题',
    `answer` TEXT NOT NULL COMMENT '答案',
    `keywords` VARCHAR(200) COMMENT '关键词，逗号分隔',
    `hit_count` INT DEFAULT 0 COMMENT '命中次数（用于优化）',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    FULLTEXT INDEX `ft_question` (`question`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI客服知识库';

-- 客服对话记录表（支持用户、机器人、客服三种消息类型）
DROP TABLE IF EXISTS `chat_record`;
CREATE TABLE `chat_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT COMMENT '用户ID（未登录为NULL）',
    `session_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `message` TEXT NOT NULL COMMENT '消息内容',
    `msg_type` VARCHAR(20) NOT NULL COMMENT '类型: user-用户, robot-机器人, 员工号-客服',
    `employee_id` BIGINT COMMENT '客服员工ID（仅当 msg_type 为员工号时有效）',
    `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读 0-未读 1-已读',
    `confidence` DECIMAL(3,2) COMMENT '置信度',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_session` (`session_id`, `created_at`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_employee_id` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服对话记录';

-- ========================================
-- 5. 初始化测试数据
-- ========================================

-- 插入测试用户
INSERT INTO `user` (`phone`, `password`, `real_name`, `id_card`, `status`) VALUES
('13800138000', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '张三', '110101199001011234', 1),
('13800138001', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '李四', '110101199002022345', 1);

INSERT INTO `station` (`name`) VALUES
('北京南'), ('济南西'), ('上海虹桥'), ('南京南'), ('广州南');

INSERT INTO `train_route_stop` (`train_no`, `station_name`, `station_no`, `arrive_time`, `depart_time`) VALUES
('G101', '北京南', 1, NULL, '08:00:00'),
('G101', '济南西', 2, '10:30:00', '10:35:00'),
('G101', '上海虹桥', 3, '13:00:00', NULL),
('G102', '上海虹桥', 1, NULL, '14:00:00'),
('G102', '济南西', 2, '16:25:00', '16:30:00'),
('G102', '北京南', 3, '19:00:00', NULL),
('G103', '北京南', 1, NULL, '09:00:00'),
('G103', '广州南', 2, '15:30:00', NULL),
('K202', '济南西', 1, NULL, '11:20:00'),
('K202', '南京南', 2, '14:05:00', '14:15:00'),
('K202', '上海虹桥', 3, '16:45:00', NULL);

INSERT INTO `train` (`id`, `train_no`, `train_type`, `start_station`, `end_station`, `start_time`, `end_time`, `status`) VALUES
(9000001, 'G101', 1, '北京南', '济南西', '08:00:00', '10:30:00', 1),
(9000002, 'G101', 1, '济南西', '上海虹桥', '10:35:00', '13:00:00', 1),
(9000101, 'G102', 1, '上海虹桥', '济南西', '14:00:00', '16:25:00', 1),
(9000102, 'G102', 1, '济南西', '北京南', '16:30:00', '19:00:00', 1),
(9000201, 'G103', 1, '北京南', '广州南', '09:00:00', '15:30:00', 1),
(9000301, 'K202', 3, '济南西', '南京南', '11:20:00', '14:05:00', 1),
(9000302, 'K202', 3, '南京南', '上海虹桥', '14:15:00', '16:45:00', 1);

INSERT INTO `ticket_stock` (`train_id`, `train_date`, `start_station`, `end_station`, `seat_type`, `price`, `total_seats`, `available_seats`, `sale_enabled`, `version`)
SELECT
    t.id,
    DATE_ADD(CURDATE(), INTERVAL n DAY) AS train_date,
    t.start_station,
    t.end_station,
    s.seat_type,
    s.price,
    s.total_seats,
    s.available_seats,
    1,
    0 AS version
FROM `train` t
CROSS JOIN (SELECT 2 AS seat_type, 500.00 AS price, 100 AS total_seats, 100 AS available_seats UNION ALL
             SELECT 3, 350.00, 200, 200 UNION ALL
             SELECT 6, 150.00, 500, 500) s
CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL
             SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) days;

-- 插入测试知识库
INSERT INTO `knowledge_base` (`category`, `question`, `answer`, `keywords`, `status`) VALUES
('booking', '如何购买火车票？', '您可以通过我们的网站或APP购买火车票。选择出发站、到达站和乘车日期后，系统会显示可用的车次，选择合适的车次和席别后，选择乘客并支付即可。', '购票,买票,如何购买', 1),
('refund', '如何退票？', '您可以在订单列表中找到需要退票的订单，点击退票按钮即可。退票后，票款将原路返回到您的支付账户。请注意，开车前一定时间退票可能会收取手续费。', '退票,退款,如何退', 1),
('query', '如何查询订单？', '登录后，点击"我的订单"即可查看您的所有订单。您可以按订单状态（待支付、已支付、已退票等）进行筛选。', '订单,查询,查订单', 1),
('common', '可以携带多少行李？', '每名旅客免费携带物品重量为：成人20千克，儿童10千克。携带物品的长、宽、高相加不得超过130厘米。超过规定重量或体积的物品需要办理托运。', '行李,携带,托运', 1);

-- 插入测试客服对话记录（演示 msg_type 新规则）
-- 注意：需要先获取用户ID和员工ID，这里使用子查询动态获取
INSERT INTO `chat_record` (`user_id`, `session_id`, `message`, `msg_type`, `employee_id`, `is_read`, `confidence`) VALUES
((SELECT id FROM `user` WHERE phone = '13800138000' LIMIT 1), 'session_001', '你好，我想查询车票。', 'user', NULL, 0, NULL),
((SELECT id FROM `user` WHERE phone = '13800138000' LIMIT 1), 'session_001', '您好，请问您要查询哪里的车票？', 'robot', NULL, 1, 0.95),
((SELECT id FROM `user` WHERE phone = '13800138000' LIMIT 1), 'session_001', '我可以帮您查询，请提供出发站和到达站。', 'EMP001', (SELECT id FROM `employee` WHERE employee_no = 'EMP001' LIMIT 1), 1, 0.98);

-- ========================================
-- 6. RBAC权限模块
-- ========================================

-- 修改用户表，添加角色ID字段
ALTER TABLE `user` ADD COLUMN `role_id` BIGINT DEFAULT NULL COMMENT '角色ID' AFTER `status`;

-- 角色表
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

-- 权限表
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

-- 角色权限关联表
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

-- 插入初始角色数据
INSERT INTO `role` (`role_name`, `role_display_name`, `description`, `status`) VALUES
('super_admin', '超级管理员', '拥有系统所有权限，可以管理用户、角色、权限等', 1),
('admin', '管理员', '拥有大部分管理权限，但无法管理系统用户和角色', 1),
('operator', '操作员', '拥有基础操作权限，如查看订单、处理退票等', 1),
('user', '普通用户', '拥有查询车票、订单管理、AI聊天、个人信息管理等基础权限', 1);

-- 插入初始权限数据
INSERT INTO `permission` (`permission_name`, `permission_display_name`, `permission_type`, `parent_id`, `path`, `component`, `icon`, `sort`, `api_method`, `api_path`, `description`, `status`) VALUES
-- 系统管理模块
('system', '系统管理', 1, 0, '/system', 'Layout', 'setting', 100, NULL, NULL, '系统管理菜单', 1),
('system:user', '用户管理', 1, 1, '/system/users', 'system/user/index', 'user', 101, NULL, NULL, '用户管理菜单', 1),
('system:user:list', '用户列表', 2, 2, NULL, NULL, NULL, 0, 'GET', '/api/admin/users', '获取用户列表', 1),
('system:user:status', '更新用户状态', 2, 2, NULL, NULL, NULL, 0, 'PUT', '/api/admin/users/{id}/status', '更新用户状态', 1),
('system:role', '角色管理', 1, 1, '/system/roles', 'system/role/index', 'role', 102, NULL, NULL, '角色管理菜单', 1),
('system:role:list', '角色列表', 2, 5, NULL, NULL, NULL, 0, 'GET', '/api/admin/roles', '获取角色列表', 1),
('system:role:add', '添加角色', 2, 5, NULL, NULL, NULL, 0, 'POST', '/api/admin/roles', '添加角色', 1),
('system:role:edit', '编辑角色', 2, 5, NULL, NULL, NULL, 0, 'PUT', '/api/admin/roles/{id}', '编辑角色', 1),
('system:role:delete', '删除角色', 2, 5, NULL, NULL, NULL, 0, 'DELETE', '/api/admin/roles/{id}', '删除角色', 1),
('system:role:assign', '权限分配', 2, 5, NULL, NULL, NULL, 0, 'POST', '/api/admin/roles/{id}/permissions', '为角色分配权限', 1),
('system:permission', '权限管理', 1, 1, '/system/permissions', 'system/permission/index', 'permission', 103, NULL, NULL, '权限管理菜单', 1),
('system:permission:list', '权限列表', 2, 11, NULL, NULL, NULL, 0, 'GET', '/api/admin/permissions', '获取权限列表', 1),
('system:permission:add', '添加权限', 2, 11, NULL, NULL, NULL, 0, 'POST', '/api/admin/permissions', '添加权限', 1),
('system:permission:edit', '编辑权限', 2, 11, NULL, NULL, NULL, 0, 'PUT', '/api/admin/permissions/{id}', '编辑权限', 1),
('system:permission:delete', '删除权限', 2, 11, NULL, NULL, NULL, 0, 'DELETE', '/api/admin/permissions/{id}', '删除权限', 1),

-- 车次管理模块
('train', '车次管理', 1, 0, '/train', 'Layout', 'train', 200, NULL, NULL, '车次管理菜单', 1),
('train:list', '车次列表', 1, 16, '/train/list', 'train/list/index', 'list', 201, NULL, NULL, '车次列表菜单', 1),
('train:list:api', '车次列表API', 3, 17, NULL, NULL, NULL, 0, 'GET', '/api/admin/trains', '获取车次列表接口', 1),
('train:add', '添加车次', 2, 17, NULL, NULL, NULL, 0, 'POST', '/api/admin/trains', '添加车次接口', 1),
('train:edit', '编辑车次', 2, 17, NULL, NULL, NULL, 0, 'PUT', '/api/admin/trains/{id}', '编辑车次接口', 1),
('train:status', '更新车次状态', 2, 17, NULL, NULL, NULL, 0, 'PUT', '/api/admin/trains/{id}/status', '更新车次状态接口', 1),
('train:stock', '设置余票', 2, 17, NULL, NULL, NULL, 0, 'POST', '/api/admin/trains/{id}/stock', '设置余票接口', 1),
('ticket:stock:sale', '线段库存开售/停售', 3, 0, NULL, NULL, NULL, 0, 'PUT', '/api/admin/ticket-stocks/{id}/sale-enabled', '更新线段席位开售状态', 1),

-- 订单管理模块
('order', '订单管理', 1, 0, '/order', 'Layout', 'order', 300, NULL, NULL, '订单管理菜单', 1),
('order:list', '订单列表', 1, 23, '/order/list', 'order/list/index', 'list', 301, NULL, NULL, '订单列表菜单', 1),
('order:list:api', '订单列表API', 3, 24, NULL, NULL, NULL, 0, 'GET', '/api/admin/orders', '获取订单列表接口', 1),
('order:refund', '退票操作', 2, 24, NULL, NULL, NULL, 0, 'POST', '/api/admin/orders/{orderNo}/refund', '退票接口', 1),

-- 知识库管理模块
('knowledge', '知识库管理', 1, 0, '/knowledge', 'Layout', 'knowledge', 400, NULL, NULL, '知识库管理菜单', 1),
('knowledge:list', '知识列表', 1, 27, '/knowledge/list', 'knowledge/list/index', 'list', 401, NULL, NULL, '知识列表菜单', 1),
('knowledge:list:api', '知识列表API', 3, 28, NULL, NULL, NULL, 0, 'GET', '/api/knowledge/list', '获取知识库列表接口', 1),
('knowledge:add', '添加知识', 2, 28, NULL, NULL, NULL, 0, 'POST', '/api/knowledge/add', '添加知识接口', 1),
('knowledge:edit', '编辑知识', 2, 28, NULL, NULL, NULL, 0, 'PUT', '/api/knowledge/update', '更新知识接口', 1),
('knowledge:delete', '删除知识', 2, 28, NULL, NULL, NULL, 0, 'DELETE', '/api/knowledge/{id}', '删除知识接口', 1),

-- 智能客服模块
('ai', '智能客服', 1, 0, '/ai', 'Layout', 'chat', 500, NULL, NULL, '智能客服菜单', 1),
('ai:chat', '对话记录', 1, 34, '/ai/chat', 'ai/chat/index', 'chat', 501, NULL, NULL, '对话记录菜单', 1),
('ai:chat:api', '对话记录API', 3, 35, NULL, NULL, NULL, 0, 'GET', '/api/chat/records', '获取对话记录接口', 1);

-- 用户端模块（普通用户权限）
INSERT INTO `permission` (`permission_name`, `permission_display_name`, `permission_type`, `parent_id`, `path`, `component`, `icon`, `sort`, `api_method`, `api_path`, `description`, `status`) VALUES
('user:train:search', '车次查询', 3, 0, NULL, NULL, NULL, 0, 'GET', '/api/trains/search', '查询车次接口', 1),
('user:train:searchRoutes', '联程车次方案', 3, 0, NULL, NULL, NULL, 0, 'GET', '/api/trains/searchRoutes', '联程/直达检索接口', 1),
('user:train:stations', '站点下拉', 3, 0, NULL, NULL, NULL, 0, 'GET', '/api/trains/stations', '站点列表接口', 1),
('user:train:detail', '车次详情', 3, 0, NULL, NULL, NULL, 0, 'GET', '/api/trains/{id}', '获取车次详情接口', 1),
('user:order:list', '订单列表', 3, 0, NULL, NULL, NULL, 0, 'GET', '/api/orders', '获取用户订单列表接口', 1),
('user:order:detail', '订单详情', 3, 0, NULL, NULL, NULL, 0, 'GET', '/api/orders/{orderNo}', '获取订单详情接口', 1),
('user:order:create', '创建订单', 3, 0, NULL, NULL, NULL, 0, 'POST', '/api/orders', '创建订单接口', 1),
('user:order:pay', '支付订单', 3, 0, NULL, NULL, NULL, 0, 'POST', '/api/orders/{orderNo}/pay', '支付订单接口', 1),
('user:order:refund', '退票', 3, 0, NULL, NULL, NULL, 0, 'POST', '/api/orders/{orderNo}/refund', '退票接口', 1),
('user:profile:view', '查看个人信息', 3, 0, NULL, NULL, NULL, 0, 'GET', '/api/user/profile', '查看个人信息接口', 1),
('user:profile:update', '更新个人信息', 3, 0, NULL, NULL, NULL, 0, 'PUT', '/api/user/profile', '更新个人信息接口', 1),
('user:passenger:list', '常用联系人列表', 3, 0, NULL, NULL, NULL, 0, 'GET', '/api/passengers', '获取常用联系人列表接口', 1),
('user:passenger:add', '添加常用联系人', 3, 0, NULL, NULL, NULL, 0, 'POST', '/api/passengers', '添加常用联系人接口', 1),
('user:passenger:edit', '编辑常用联系人', 3, 0, NULL, NULL, NULL, 0, 'PUT', '/api/passengers/{id}', '编辑常用联系人接口', 1),
('user:passenger:delete', '删除常用联系人', 3, 0, NULL, NULL, NULL, 0, 'DELETE', '/api/passengers/{id}', '删除常用联系人接口', 1),
('user:chat:send', '发送聊天消息', 3, 0, NULL, NULL, NULL, 0, 'POST', '/api/chat', '发送聊天消息接口', 1),
('user:chat:records', '聊天记录', 3, 0, NULL, NULL, NULL, 0, 'GET', '/api/chat/records', '获取聊天记录接口', 1),
('user:customer_service:request', '请求人工客服', 3, 0, NULL, NULL, NULL, 0, 'POST', '/api/customer-service/request-human', '请求人工客服接口', 1),
('user:customer_service:history', '客服对话历史', 3, 0, NULL, NULL, NULL, 0, 'GET', '/api/customer-service/history', '获取客服对话历史接口', 1),
('user:knowledge:enabled', '获取知识库', 3, 0, NULL, NULL, NULL, 0, 'GET', '/api/knowledge/enabled', '获取已启用知识库接口', 1);

-- 统计管理模块
INSERT INTO `permission` (`permission_name`, `permission_display_name`, `permission_type`, `parent_id`, `path`, `component`, `icon`, `sort`, `api_method`, `api_path`, `description`, `status`) VALUES
('stats:overview:api', '数据概览API', 3, 0, NULL, NULL, NULL, 0, 'GET', '/api/admin/stats/overview', '获取数据概览接口', 1),
('stats:trend:api', '趋势分析API', 3, 0, NULL, NULL, NULL, 0, 'GET', '/api/admin/stats/trend/{type}', '获取趋势分析接口', 1);

-- 为超级管理员分配所有权限
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p WHERE r.role_name = 'super_admin';

-- 为管理员分配除系统管理外的所有权限（即排除系统管理模块）
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p 
WHERE r.role_name = 'admin' 
  AND p.permission_name NOT LIKE 'system%' 
  AND p.permission_name NOT IN ('system');

-- 为操作员分配基础权限：查看车次列表、查看订单列表、查看知识库列表
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p 
WHERE r.role_name = 'operator' 
  AND p.permission_name IN (
    'train:list:api', 
    'order:list:api', 
    'knowledge:list:api',
    'ai:chat:api'
  );

-- 为普通用户分配基础权限
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `role` r, `permission` p 
WHERE r.role_name = 'user' 
  AND p.permission_name IN (
    'user:train:search',
    'user:train:searchRoutes',
    'user:train:stations',
    'user:train:detail',
    'user:order:list',
    'user:order:detail',
    'user:order:create',
    'user:order:pay',
    'user:order:refund',
    'user:profile:view',
    'user:profile:update',
    'user:passenger:list',
    'user:passenger:add',
    'user:passenger:edit',
    'user:passenger:delete',
    'user:chat:send',
    'user:chat:records',
    'user:customer_service:request',
    'user:customer_service:history',
    'user:knowledge:enabled'
  );

-- 更新测试用户的角色
UPDATE `user` SET `role_id` = (SELECT `id` FROM `role` WHERE `role_name` = 'super_admin') WHERE `phone` = '13800138000';
UPDATE `user` SET `role_id` = (SELECT `id` FROM `role` WHERE `role_name` = 'admin') WHERE `phone` = '13800138001';

-- ========================================
-- 7. 操作日志模块
-- ========================================

-- 操作日志表
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT COMMENT '操作用户ID',
    `username` VARCHAR(50) COMMENT '用户名',
    `operation` VARCHAR(100) NOT NULL COMMENT '操作类型',
    `module` VARCHAR(50) COMMENT '操作模块',
    `description` VARCHAR(500) COMMENT '操作描述',
    `request_method` VARCHAR(10) COMMENT '请求方法',
    `request_url` VARCHAR(500) COMMENT '请求URL',
    `request_params` TEXT COMMENT '请求参数',
    `ip_address` VARCHAR(50) COMMENT 'IP地址',
    `user_agent` VARCHAR(500) COMMENT '用户代理',
    `status` TINYINT DEFAULT 1 COMMENT '操作状态 1-成功 0-失败',
    `error_message` TEXT COMMENT '错误信息',
    `execution_time` INT COMMENT '执行耗时(ms)',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_created_at` (`created_at`),
    INDEX `idx_operation` (`operation`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- ========================================
-- 8. 员工管理模块（与用户表严格隔离）
-- ========================================

-- 部门表
DROP TABLE IF EXISTS `department`;
CREATE TABLE `department` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '部门ID',
    `dept_code` VARCHAR(50) NOT NULL COMMENT '部门编码',
    `dept_name` VARCHAR(100) NOT NULL COMMENT '部门名称',
    `description` VARCHAR(500) COMMENT '部门描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0-禁用 1-启用',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_dept_code` (`dept_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

-- 员工表
DROP TABLE IF EXISTS `employee`;
CREATE TABLE `employee` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '员工ID',
    `employee_no` VARCHAR(50) NOT NULL COMMENT '员工工号（唯一）',
    `name` VARCHAR(50) NOT NULL COMMENT '员工姓名',
    `gender` TINYINT COMMENT '性别 0-未知 1-男 2-女',
    `phone` VARCHAR(20) COMMENT '手机号',
    `password` VARCHAR(100) NOT NULL COMMENT '加密密码' DEFAULT '$2a$10$vI8aWBnW3fID.ZQ4/zo1G.q1lRps.9cGLcZEiGDMVr5yUP1KUOYTa',
    `email` VARCHAR(100) COMMENT '邮箱',
    `department_id` BIGINT COMMENT '所属部门ID',
    `position` VARCHAR(100) COMMENT '职位',
    `hire_date` DATE COMMENT '入职日期',
    `id_card` VARCHAR(100) COMMENT '身份证号（加密存储）',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0-离职 1-在职',
    `role_id` BIGINT DEFAULT NULL COMMENT '角色ID',
    `remark` VARCHAR(500) COMMENT '备注',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_employee_no` (`employee_no`),
    UNIQUE KEY `uk_phone` (`phone`),
    UNIQUE KEY `uk_email` (`email`),
    INDEX `idx_department_id` (`department_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_role_id` (`role_id`),
    CONSTRAINT `fk_employee_department` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `fk_employee_role` FOREIGN KEY (`role_id`) REFERENCES `role` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工表';

-- 添加 chat_record 表的外键约束（引用 employee 表）
ALTER TABLE `chat_record`
ADD CONSTRAINT `fk_chat_record_employee`
FOREIGN KEY (`employee_id`) REFERENCES `employee` (`id`)
ON DELETE SET NULL ON UPDATE CASCADE;

-- 插入初始部门数据
INSERT INTO `department` (`dept_code`, `dept_name`, `description`, `status`) VALUES
('IT', '技术部', '负责系统开发与维护', 1),
('HR', '人力资源部', '负责招聘与员工关系', 1),
('OP', '运营部', '负责业务运营与客服', 1),
('FIN', '财务部', '负责财务管理', 1);

-- 插入初始员工数据
INSERT INTO `employee` (`employee_no`, `name`, `gender`, `phone`, `password`, `email`, `department_id`, `position`, `hire_date`, `id_card`, `status`, `role_id`, `remark`) VALUES
('EMP001', '王经理', 1, '13900139000', '$2a$10$vI8aWBnW3fID.ZQ4/zo1G.q1lRps.9cGLcZEiGDMVr5yUP1KUOYTa', 'wang@example.com', (SELECT id FROM department WHERE dept_code = 'IT'), '技术经理', '2020-01-01', '110101198001011234', 1, (SELECT id FROM role WHERE role_name = 'admin'), '技术负责人'),
('EMP002', '李主管', 2, '13900139001', '$2a$10$vI8aWBnW3fID.ZQ4/zo1G.q1lRps.9cGLcZEiGDMVr5yUP1KUOYTa', 'li@example.com', (SELECT id FROM department WHERE dept_code = 'HR'), '人事主管', '2021-03-15', '110101198102022345', 1, (SELECT id FROM role WHERE role_name = 'operator'), '招聘负责人'),
('EMP003', '张运营', 1, '13900139002', '$2a$10$vI8aWBnW3fID.ZQ4/zo1G.q1lRps.9cGLcZEiGDMVr5yUP1KUOYTa', 'zhang@example.com', (SELECT id FROM department WHERE dept_code = 'OP'), '运营专员', '2022-06-20', '110101198203033456', 1, (SELECT id FROM role WHERE role_name = 'operator'), '客服运营');

-- 启用外键检查（恢复数据库完整性约束）
SET FOREIGN_KEY_CHECKS = 1;
