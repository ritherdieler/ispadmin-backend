-- ACS last-state WiFi snapshot for Inform push (series live in Core).
SET @ddl = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'cpe_record'
    ) OR EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'cpe_record'
          AND COLUMN_NAME = 'wifi_snapshot_json'
    ),
    'SELECT 1',
    'ALTER TABLE cpe_record ADD COLUMN wifi_snapshot_json TEXT NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'cpe_record'
    ) OR EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'cpe_record'
          AND COLUMN_NAME = 'wifi_associated_2g'
    ),
    'SELECT 1',
    'ALTER TABLE cpe_record ADD COLUMN wifi_associated_2g INT NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'cpe_record'
    ) OR EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'cpe_record'
          AND COLUMN_NAME = 'wifi_associated_5g'
    ),
    'SELECT 1',
    'ALTER TABLE cpe_record ADD COLUMN wifi_associated_5g INT NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'cpe_record'
    ) OR EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'cpe_record'
          AND COLUMN_NAME = 'wifi_associated_total'
    ),
    'SELECT 1',
    'ALTER TABLE cpe_record ADD COLUMN wifi_associated_total INT NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'cpe_record'
    ) OR EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'cpe_record'
          AND COLUMN_NAME = 'wifi_observed_at'
    ),
    'SELECT 1',
    'ALTER TABLE cpe_record ADD COLUMN wifi_observed_at DATETIME(6) NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'cpe_record'
    ) OR EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'cpe_record'
          AND COLUMN_NAME = 'wifi_quality_status'
    ),
    'SELECT 1',
    'ALTER TABLE cpe_record ADD COLUMN wifi_quality_status VARCHAR(32) NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
