SET @exist := (
    SELECT COUNT(*)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'onu'
);
SET @col := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'onu'
      AND COLUMN_NAME = 'unique_external_id'
);
SET @sql := IF(
    @exist = 0 OR @col > 0,
    'SELECT 1',
    'ALTER TABLE onu ADD COLUMN unique_external_id VARCHAR(128) NULL'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
