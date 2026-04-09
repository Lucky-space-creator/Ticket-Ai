-- ========================================
-- 12306购票系统数据库建表SQL
-- ========================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `ticket_system` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `ticket_system`;

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
-- 2. 车次模块
-- ========================================

-- 车次表（简化：移除运行天数等次要字段）
DROP TABLE IF EXISTS `train`;
CREATE TABLE `train` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `train_no` VARCHAR(20) NOT NULL COMMENT '车次号 如G1234',
    `train_type` TINYINT NOT NULL COMMENT '类型 1-高铁 2-动车 3-普快',
    `start_station` VARCHAR(50) NOT NULL COMMENT '始发站',
    `end_station` VARCHAR(50) NOT NULL COMMENT '终到站',
    `start_time` TIME NOT NULL COMMENT '发车时间',
    `end_time` TIME NOT NULL COMMENT '到达时间',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0-停运 1-正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_train_no` (`train_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车次基础信息表';

-- 车次停靠站表（保留核心字段）
DROP TABLE IF EXISTS `train_station`;
CREATE TABLE `train_station` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `train_id` BIGINT NOT NULL COMMENT '车次ID',
    `station_name` VARCHAR(50) NOT NULL COMMENT '站点名称',
    `station_no` INT NOT NULL COMMENT '站序(第几站)',
    `arrive_time` TIME COMMENT '到达时间',
    `depart_time` TIME COMMENT '出发时间',
    INDEX `idx_train_id` (`train_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车次停靠站时刻表';

-- 余票库存表（简化：移除乐观锁版本号，使用数据库锁）
DROP TABLE IF EXISTS `ticket_stock`;
CREATE TABLE `ticket_stock` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `train_id` BIGINT NOT NULL COMMENT '车次ID',
    `train_date` DATE NOT NULL COMMENT '乘车日期',
    `start_station` VARCHAR(50) NOT NULL COMMENT '出发站',
    `end_station` VARCHAR(50) NOT NULL COMMENT '到达站',
    `seat_type` TINYINT NOT NULL COMMENT '席别 1-商务 2-一等 3-二等 4-软卧 5-硬卧 6-硬座',
    `price` DECIMAL(10,2) NOT NULL COMMENT '票价',
    `available_seats` INT NOT NULL DEFAULT 0 COMMENT '剩余座位数',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_stock` (`train_id`, `train_date`, `start_station`, `end_station`, `seat_type`),
    INDEX `idx_query` (`train_date`, `start_station`, `end_station`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='余票库存表';

-- ========================================
-- 3. 订单模块
-- ========================================

-- 订单主表（合并状态，简化支付相关字段）
DROP TABLE IF EXISTS `order`;
CREATE TABLE `order` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `train_id` BIGINT NOT NULL COMMENT '车次ID',
    `train_no` VARCHAR(20) NOT NULL COMMENT '车次号',
    `train_date` DATE NOT NULL COMMENT '乘车日期',
    `start_station` VARCHAR(50) NOT NULL COMMENT '出发站',
    `end_station` VARCHAR(50) NOT NULL COMMENT '到达站',
    `depart_time` DATETIME NOT NULL COMMENT '发车时间',
    `seat_type` TINYINT NOT NULL COMMENT '席别',
    `total_amount` DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0-待支付 1-已支付 2-已退票 3-已取消',
    `pay_time` DATETIME COMMENT '支付时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_order_no` (`order_no`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单主表';

-- 订单乘客明细表（简化：移除座位号、电子客票号）
DROP TABLE IF EXISTS `order_item`;
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

-- 客服对话记录表（简化：移除置信度等AI细节字段）
DROP TABLE IF EXISTS `chat_record`;
CREATE TABLE `chat_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT COMMENT '用户ID（未登录为NULL）',
    `session_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
    `message` TEXT NOT NULL COMMENT '消息内容',
    `msg_type` TINYINT NOT NULL COMMENT '类型 1-用户 2-机器人',
    `confidence` DECIMAL(3,2) COMMENT '置信度',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_session` (`session_id`, `created_at`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服对话记录';

-- ========================================
-- 5. 初始化测试数据
-- ========================================

-- 插入测试用户
INSERT INTO `user` (`phone`, `password`, `real_name`, `id_card`, `status`) VALUES
('13800138000', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '张三', '110101199001011234', 1),
('13800138001', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '李四', '110101199002022345', 1);

-- 插入测试车次
INSERT INTO `train` (`train_no`, `train_type`, `start_station`, `end_station`, `start_time`, `end_time`, `status`) VALUES
('G101', 1, '北京南', '上海虹桥', '08:00:00', '13:00:00', 1),
('G102', 1, '上海虹桥', '北京南', '14:00:00', '19:00:00', 1),
('G103', 1, '北京南', '广州南', '09:00:00', '15:30:00', 1);

-- 插入测试余票库存（未来7天）
INSERT INTO `ticket_stock` (`train_id`, `train_date`, `start_station`, `end_station`, `seat_type`, `price`, `available_seats`)
SELECT
    t.id,
    DATE_ADD(CURDATE(), INTERVAL n DAY) AS train_date,
    t.start_station,
    t.end_station,
    s.seat_type,
    s.price,
    s.available_seats
FROM `train` t
CROSS JOIN (SELECT 2 AS seat_type, 500.00 AS price, 100 AS available_seats UNION ALL
             SELECT 3, 350.00, 200 UNION ALL
             SELECT 6, 150.00, 500) s
CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL
             SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6) days;

-- 插入测试知识库
INSERT INTO `knowledge_base` (`category`, `question`, `answer`, `keywords`, `status`) VALUES
('booking', '如何购买火车票？', '您可以通过我们的网站或APP购买火车票。选择出发站、到达站和乘车日期后，系统会显示可用的车次，选择合适的车次和席别后，选择乘客并支付即可。', '购票,买票,如何购买', 1),
('refund', '如何退票？', '您可以在订单列表中找到需要退票的订单，点击退票按钮即可。退票后，票款将原路返回到您的支付账户。请注意，开车前一定时间退票可能会收取手续费。', '退票,退款,如何退', 1),
('query', '如何查询订单？', '登录后，点击"我的订单"即可查看您的所有订单。您可以按订单状态（待支付、已支付、已退票等）进行筛选。', '订单,查询,查订单', 1),
('common', '可以携带多少行李？', '每名旅客免费携带物品重量为：成人20千克，儿童10千克。携带物品的长、宽、高相加不得超过130厘米。超过规定重量或体积的物品需要办理托运。', '行李,携带,托运', 1);
