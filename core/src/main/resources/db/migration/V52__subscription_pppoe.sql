-- V52: access_mode + PPPoE / management columns. DDL only.
-- Leave every row at STATIC_IP. Do not backfill PPPOE_FIXED or usernames here.
-- UNIQUE is safe because pppoe_username stays NULL (MySQL allows many NULLs).
-- Resolve duplicate IPs before any out-of-band username backfill
-- (scripts/sql/backfill-subscription-pppoe-legacy.sql).
-- Idempotente: staging ya tiene las columnas (Hibernate) y un UNIQUE con otro nombre.

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription'
    ) OR EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription' AND COLUMN_NAME = 'access_mode'
    ),
    'SELECT 1',
    'ALTER TABLE `subscription` ADD COLUMN `access_mode` VARCHAR(16) NOT NULL DEFAULT ''STATIC_IP'''
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription'
    ) OR EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription' AND COLUMN_NAME = 'pppoe_username'
    ),
    'SELECT 1',
    'ALTER TABLE `subscription` ADD COLUMN `pppoe_username` VARCHAR(64) NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription'
    ) OR EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription' AND COLUMN_NAME = 'pppoe_password_enc'
    ),
    'SELECT 1',
    'ALTER TABLE `subscription` ADD COLUMN `pppoe_password_enc` VARCHAR(512) NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription'
    ) OR EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription' AND COLUMN_NAME = 'pppoe_profile'
    ),
    'SELECT 1',
    'ALTER TABLE `subscription` ADD COLUMN `pppoe_profile` VARCHAR(64) NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription'
    ) OR EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription' AND COLUMN_NAME = 'pppoe_provision_status'
    ),
    'SELECT 1',
    'ALTER TABLE `subscription` ADD COLUMN `pppoe_provision_status` VARCHAR(24) NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription'
    ) OR EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription' AND COLUMN_NAME = 'pppoe_last_ip'
    ),
    'SELECT 1',
    'ALTER TABLE `subscription` ADD COLUMN `pppoe_last_ip` VARCHAR(45) NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription'
    ) OR EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription' AND COLUMN_NAME = 'management_ip'
    ),
    'SELECT 1',
    'ALTER TABLE `subscription` ADD COLUMN `management_ip` VARCHAR(45) NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription'
    ) OR EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription' AND COLUMN_NAME = 'management_mac'
    ),
    'SELECT 1',
    'ALTER TABLE `subscription` ADD COLUMN `management_mac` VARCHAR(32) NULL'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription'
    ) OR EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription'
          AND INDEX_NAME = 'uk_subscription_pppoe_username'
    ) OR EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription'
          AND COLUMN_NAME = 'pppoe_username' AND NON_UNIQUE = 0
    ),
    'SELECT 1',
    'ALTER TABLE `subscription` ADD UNIQUE KEY `uk_subscription_pppoe_username` (`pppoe_username`)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription'
    ) OR EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription'
          AND INDEX_NAME = 'idx_subscription_access_mode'
    ),
    'SELECT 1',
    'CREATE INDEX `idx_subscription_access_mode` ON `subscription` (`access_mode`)'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
