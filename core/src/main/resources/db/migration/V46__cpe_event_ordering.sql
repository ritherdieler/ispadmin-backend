SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription'
    ) OR EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription' AND COLUMN_NAME = 'tr069_state_observed_at'
    ),
    'SELECT 1',
    'ALTER TABLE subscription ADD COLUMN tr069_state_observed_at DATETIME(6) NULL, ADD COLUMN tr069_state_event_id VARCHAR(128) NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
