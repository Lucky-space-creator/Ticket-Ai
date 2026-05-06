-- =============================================================================
-- 联程分段 / 订单 SKU 相关 — 仅 DDL 增量（CREATE / ALTER）
-- 适用：已有业务库、已有表与数据，不能执行整份 init.sql 中 DROP+CREATE 的场景。
--
-- 使用前请确认：
--   1) 已 USE 到你的目标库（或把下面库名改成你的库名）。
--   2) 下方「加列 / 加索引」已按 information_schema 做存在性判断，可重复执行，不会因为 Duplicate column/key 中断。
--   3) train 从「整程一行」迁到「线段一行」属于数据迁移 + 约束调整，仅 ALTER 无法自动拆数据；
--      若你仍是旧 train 模型，请先完成拆段与 ticket_stock.train_id 对齐，再调整唯一索引（见文末说明）。
-- =============================================================================

-- USE `ticket_system`;

SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- 1) 新表：站点主数据、停靠序、订单行程段
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `station` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `name` VARCHAR(50) NOT NULL COMMENT '站名（与线段/停靠表一致）',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_station_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站点主数据';

CREATE TABLE IF NOT EXISTS `train_route_stop` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `train_no` VARCHAR(20) NOT NULL COMMENT '车次号',
    `station_name` VARCHAR(50) NOT NULL COMMENT '站点名称',
    `station_no` INT NOT NULL COMMENT '站序(第几站)',
    `arrive_time` TIME NULL COMMENT '到达时间',
    `depart_time` TIME NULL COMMENT '出发时间',
    UNIQUE KEY `uk_train_stop` (`train_no`, `station_no`),
    INDEX `idx_train_no` (`train_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车次停靠站时刻表';

CREATE TABLE IF NOT EXISTS `order_route_leg` (
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

-- ---------------------------------------------------------------------------
-- 2) ticket_stock：总座、开售开关（与 ticket-common TicketStock / 停售闸一致）
--    无则 ADD，已有则跳过（避免 1060 Duplicate column）。
-- ---------------------------------------------------------------------------

SET @exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket_stock' AND COLUMN_NAME = 'total_seats');
SET @sql := IF(@exists = 0,
    'ALTER TABLE `ticket_stock` ADD COLUMN `total_seats` INT NOT NULL DEFAULT 0 COMMENT ''总座位数'' AFTER `price`',
    'SELECT ''ticket_stock.total_seats 已存在，跳过'' AS _Migration');
PREPARE _stmt FROM @sql;
EXECUTE _stmt;
DEALLOCATE PREPARE _stmt;

SET @exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket_stock' AND COLUMN_NAME = 'sale_enabled');
SET @sql := IF(@exists = 0,
    'ALTER TABLE `ticket_stock` ADD COLUMN `sale_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT ''是否允许售票 0-停售 1-开售'' AFTER `available_seats`',
    'SELECT ''ticket_stock.sale_enabled 已存在，跳过'' AS _Migration');
PREPARE _stmt FROM @sql;
EXECUTE _stmt;
DEALLOCATE PREPARE _stmt;

-- ---------------------------------------------------------------------------
-- 3) `order`：联程 SKU、首段占位、可空 train 展示字段（与新版实体一致）
-- ---------------------------------------------------------------------------

ALTER TABLE `order`
    MODIFY COLUMN `train_id` BIGINT NULL COMMENT '兼容/展示：首段线段ID，非业务真源';

ALTER TABLE `order`
    MODIFY COLUMN `train_no` VARCHAR(20) NULL COMMENT '兼容/展示：首段车次号';

SET @exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order' AND COLUMN_NAME = 'route_sku');
SET @sql := IF(@exists = 0,
    'ALTER TABLE `order` ADD COLUMN `route_sku` VARCHAR(256) NULL COMMENT ''线段id按序拼接'' AFTER `train_no`',
    'SELECT ''order.route_sku 已存在，跳过'' AS _Migration');
PREPARE _stmt FROM @sql;
EXECUTE _stmt;
DEALLOCATE PREPARE _stmt;

SET @exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order' AND COLUMN_NAME = 'route_type');
SET @sql := IF(@exists = 0,
    'ALTER TABLE `order` ADD COLUMN `route_type` VARCHAR(20) NULL COMMENT ''DIRECT/TRANSFER/SINGLE'' AFTER `route_sku`',
    'SELECT ''order.route_type 已存在，跳过'' AS _Migration');
PREPARE _stmt FROM @sql;
EXECUTE _stmt;
DEALLOCATE PREPARE _stmt;

-- ---------------------------------------------------------------------------
-- 4) train：线段语义下的唯一索引（若你库中尚无此索引再执行）
--    索引名 uk_train_segment 与 init.sql / 订票搜索一致。
-- ---------------------------------------------------------------------------

SET @exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'train' AND INDEX_NAME = 'uk_train_segment');
SET @sql := IF(@exists = 0,
    'ALTER TABLE `train` ADD UNIQUE KEY `uk_train_segment` (`train_no`, `start_station`, `end_station`)',
    'SELECT ''train.uk_train_segment 已存在，跳过'' AS _Migration');
PREPARE _stmt FROM @sql;
EXECUTE _stmt;
DEALLOCATE PREPARE _stmt;

-- ---------------------------------------------------------------------------
-- 5) train：按车次号线段列表（若尚无 idx_train_no 再执行）
-- ---------------------------------------------------------------------------

SET @exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'train' AND INDEX_NAME = 'idx_train_no');
SET @sql := IF(@exists = 0,
    'ALTER TABLE `train` ADD INDEX `idx_train_no` (`train_no`)',
    'SELECT ''train.idx_train_no 已存在，跳过'' AS _Migration');
PREPARE _stmt FROM @sql;
EXECUTE _stmt;
DEALLOCATE PREPARE _stmt;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 【非 DDL】权限两行（网关鉴权）：若你希望与 init.sql 行为一致，在 permission / role_permission
-- 中建好数据后可手工执行 INSERT（以下为示例，避免因 permission.id 不确定导致冲突，生产请按你们规范入库）：
--
-- INSERT IGNORE INTO permission (permission_name, permission_display_name, permission_type,
--   parent_id, path, component, icon, sort, api_method, api_path, description, status)
-- VALUES ('user:train:searchRoutes','联程车次方案',3,0,NULL,NULL,NULL,0,'GET','/api/trains/searchRoutes','联程/直达检索',1),
--        ('user:train:stations','站点下拉',3,0,NULL,NULL,NULL,0,'GET','/api/trains/stations','站点列表',1),
--        ('ticket:stock:sale','线段库存开售/停售',3,0,NULL,NULL,NULL,0,'PUT','/api/admin/ticket-stocks/{id}/sale-enabled','停售开关',1);
--
-- 再将这两条挂到 role `user`、`admin/super_admin` 等你需要的角色（见业务 init.sql）。
-- =============================================================================
--
-- 【train 模型说明】当前 init.sql 设计为：`train.id` = 线段主键（常为种子 ID），唯一键 (`train_no`,`start_station`,`end_station`)。
-- 若旧库是 AUTO_INCREMENT + 单程一行，`train.id` / `ticket_stock.train_id` 与代码「线段」语义不一致，需要单独做数据拆分脚本，
-- 本文件不包含数据迁移 DELETE/UPDATE。
-- =============================================================================
