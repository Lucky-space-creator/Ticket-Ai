-- 在MySQL客户端中依次执行
ALTER TABLE `ticket_stock`
    ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号'
        AFTER `available_seats`;

ALTER TABLE `ticket_stock`
    ADD COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        AFTER `version`;
