SET @has_year_month = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'subscription_traffic_monthly'
      AND COLUMN_NAME = 'year_month'
);

SET @rename_sql = IF(
    @has_year_month > 0,
    'ALTER TABLE subscription_traffic_monthly CHANGE COLUMN year_month month_key CHAR(7) NOT NULL',
    'SELECT 1'
);

PREPARE rename_stmt FROM @rename_sql;
EXECUTE rename_stmt;
DEALLOCATE PREPARE rename_stmt;
