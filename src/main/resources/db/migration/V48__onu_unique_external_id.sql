SET @exist := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'onu'
      AND COLUMN_NAME = 'unique_external_id'
);
SET @sql := IF(
    @exist = 0,
    'ALTER TABLE onu ADD COLUMN unique_external_id VARCHAR(128) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
