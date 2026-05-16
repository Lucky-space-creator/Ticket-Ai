-- ========================================
-- 今日测试车次数据（用于测试下单/退款流程）
-- 执行前请确保 init.sql 已执行
-- ========================================

USE `ticket_system`;

-- 1. 插入新的测试车次（北京南→上海虹桥 直达）
INSERT INTO `train` (`id`, `train_no`, `train_type`, `start_station`, `end_station`, `start_time`, `end_time`, `status`) VALUES
(9001001, 'G201', 1, '北京南', '上海虹桥', '07:00:00', '11:30:00', 1),
(9001002, 'G202', 1, '上海虹桥', '北京南', '12:00:00', '16:30:00', 1),
(9001003, 'G203', 1, '北京南', '上海虹桥', '14:00:00', '18:30:00', 1),
(9001004, 'G204', 1, '北京南', '南京南', '08:30:00', '11:00:00', 1),
(9001005, 'G205', 1, '南京南', '上海虹桥', '11:30:00', '12:30:00', 1)
ON DUPLICATE KEY UPDATE `status` = VALUES(`status`);

-- 2. 插入停靠站时刻表
INSERT INTO `train_route_stop` (`train_no`, `station_name`, `station_no`, `arrive_time`, `depart_time`) VALUES
('G201', '北京南', 1, NULL, '07:00:00'),
('G201', '上海虹桥', 2, '11:30:00', NULL),
('G202', '上海虹桥', 1, NULL, '12:00:00'),
('G202', '北京南', 2, '16:30:00', NULL),
('G203', '北京南', 1, NULL, '14:00:00'),
('G203', '上海虹桥', 2, '18:30:00', NULL),
('G204', '北京南', 1, NULL, '08:30:00'),
('G204', '南京南', 2, '11:00:00', NULL),
('G205', '南京南', 1, NULL, '11:30:00'),
('G205', '上海虹桥', 2, '12:30:00', NULL)
ON DUPLICATE KEY UPDATE `arrive_time` = VALUES(`arrive_time`), `depart_time` = VALUES(`depart_time`);

-- 3. 为今天的车次插入库存（3种席别：一等座/二等座/硬座）
-- 今天的日期用 CURDATE()
INSERT INTO `ticket_stock` (`train_id`, `train_date`, `start_station`, `end_station`, `seat_type`, `price`, `total_seats`, `available_seats`, `sale_enabled`, `version`)
SELECT
    t.id,
    CURDATE(),
    t.start_station,
    t.end_station,
    s.seat_type,
    s.price,
    s.total_seats,
    s.available_seats,
    1,
    0
FROM `train` t
CROSS JOIN (
    SELECT 2 AS seat_type, 600.00 AS price, 50 AS total_seats, 50 AS available_seats UNION ALL
    SELECT 3, 400.00, 100, 100 UNION ALL
    SELECT 6, 200.00, 200, 200
) s
WHERE t.train_no IN ('G201', 'G202', 'G203', 'G204', 'G205')
ON DUPLICATE KEY UPDATE `available_seats` = VALUES(`available_seats`), `sale_enabled` = 1;

-- 4. 验证数据
SELECT '=== 今日测试车次 ===' AS info;
SELECT t.train_no, t.start_station, t.end_station, t.start_time, t.end_time
FROM train t
WHERE t.train_no IN ('G201', 'G202', 'G203', 'G204', 'G205')
ORDER BY t.train_no, t.start_station;

SELECT '=== 今日库存 ===' AS info;
SELECT ts.train_date, t.train_no, ts.start_station, ts.end_station,
       CASE ts.seat_type WHEN 1 THEN '商务' WHEN 2 THEN '一等' WHEN 3 THEN '二等' WHEN 6 THEN '硬座' END AS seat,
       ts.price, ts.available_seats, ts.sale_enabled
FROM ticket_stock ts
JOIN train t ON ts.train_id = t.id
WHERE ts.train_date = CURDATE()
  AND t.train_no IN ('G201', 'G202', 'G203', 'G204', 'G205')
ORDER BY t.train_no, ts.start_station, ts.seat_type;
