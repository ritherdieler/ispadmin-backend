SET @ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'tr069_model_profile'
          AND COLUMN_NAME = 'client_wan_ppp_connection_path'
    ),
    'SELECT 1',
    'ALTER TABLE tr069_model_profile ADD COLUMN client_wan_ppp_connection_path VARCHAR(512) NULL AFTER client_wan_ip_connection_path'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
