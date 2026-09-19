SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'error_log'
    ),
    'SELECT 1',
    'ALTER TABLE error_log MODIFY COLUMN error TEXT NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
