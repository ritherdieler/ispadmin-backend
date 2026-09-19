-- Indexes for the Inform-driven 360 at fleet scale.
--
-- At 3000 ONUs informing every 30 min: 144k count rows/day (~13M at
-- countRetentionDays=90) and ~576k station rows/day (~8M at
-- stationRetentionDays=14).
--
-- 1. Every Inform runs the idempotency check
--    findByDeviceIdAndSubscriptionIdAndInformAt. V36 replaced the inform_at
--    unique key with one on observed_at, leaving that lookup to scan every row
--    of the device (~4300 at 90 days) 1.67 times a second.
-- 2. The retention purge deletes by inform_at / observed_at alone, which the
--    (subscription_id, observed_at) index cannot serve.

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acs_wifi_count_sample'
    ) OR EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acs_wifi_count_sample' AND INDEX_NAME = 'idx_sh_wifi_device_inform'
    ),
    'SELECT 1',
    'CREATE INDEX `idx_sh_wifi_device_inform` ON `acs_wifi_count_sample` (`device_id`, `subscription_id`, `inform_at`)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acs_wifi_count_sample'
    ) OR EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acs_wifi_count_sample' AND INDEX_NAME = 'idx_sh_wifi_inform_at'
    ),
    'SELECT 1',
    'CREATE INDEX `idx_sh_wifi_inform_at` ON `acs_wifi_count_sample` (`inform_at`)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acs_wifi_station_sample'
    ) OR EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'acs_wifi_station_sample' AND INDEX_NAME = 'idx_sh_station_observed_at'
    ),
    'SELECT 1',
    'CREATE INDEX `idx_sh_station_observed_at` ON `acs_wifi_station_sample` (`observed_at`)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
