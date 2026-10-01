-- A customer may hold several subscriptions; duplicates are prevented by client_request_id.
SET @dni_unique_index = (
    SELECT s.INDEX_NAME FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = DATABASE() AND s.TABLE_NAME = 'subscription'
      AND s.COLUMN_NAME = 'dni' AND s.NON_UNIQUE = 0
      AND (SELECT COUNT(*) FROM information_schema.STATISTICS c
           WHERE c.TABLE_SCHEMA = s.TABLE_SCHEMA AND c.TABLE_NAME = s.TABLE_NAME AND c.INDEX_NAME = s.INDEX_NAME) = 1
    LIMIT 1
);
SET @ddl = IF(@dni_unique_index IS NULL, 'SELECT 1', CONCAT('ALTER TABLE `subscription` DROP INDEX `', @dni_unique_index, '`'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription' AND INDEX_NAME = 'idx_subscription_dni'
    ),
    'SELECT 1',
    'CREATE INDEX `idx_subscription_dni` ON `subscription` (`dni`)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
