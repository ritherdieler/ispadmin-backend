-- F2: dieta de netdiag.
-- Fusiona net_diag_alert_decision en net_diag_incident_event, agrega el contador de supresiones
-- y crea los indices que faltan para /api/netdiag/olt/logs y para la purga por fecha.
-- Idempotente: hasta F9 estas estructuras las crea tambien ddl-auto=update.

DROP PROCEDURE IF EXISTS netdiag_add_column_if_missing;
DROP PROCEDURE IF EXISTS netdiag_add_index_if_missing;

DELIMITER $$

CREATE PROCEDURE netdiag_add_column_if_missing(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE netdiag_add_index_if_missing(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_columns VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = CONCAT('CREATE INDEX `', p_index, '` ON `', p_table, '` (', p_columns, ')');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CREATE TABLE IF NOT EXISTS net_diag_alert_suppression (
    id BIGINT NOT NULL AUTO_INCREMENT,
    incident_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    window_start DATETIME(6) NOT NULL,
    event_count BIGINT NOT NULL DEFAULT 0,
    first_seen_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ndas_window (incident_id, target_id, reason_code, window_start),
    KEY idx_ndas_window_start (window_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CALL netdiag_add_column_if_missing('net_diag_incident_event', 'reason_code', 'VARCHAR(64) NULL');
CALL netdiag_add_column_if_missing('net_diag_incident_event', 'target_id', 'BIGINT NULL');

CALL netdiag_add_index_if_missing('net_diag_olt_log_event', 'idx_ndole_received', '`received_at`');
CALL netdiag_add_index_if_missing('net_diag_olt_log_event', 'idx_ndole_board_port_received', '`board`,`port`,`received_at`');
CALL netdiag_add_index_if_missing('net_diag_olt_log_event', 'idx_ndole_unparsed_received', '`is_unparsed`,`received_at`');
CALL netdiag_add_index_if_missing('net_diag_olt_log_event', 'idx_ndole_target_received', '`target_id`,`received_at`');
CALL netdiag_add_index_if_missing('net_diag_incident_event', 'idx_ndie_created', '`created_at`');
CALL netdiag_add_index_if_missing('net_diag_incident_event', 'idx_ndie_incident_created', '`incident_id`,`created_at`');
CALL netdiag_add_index_if_missing('net_diag_alert_decision', 'idx_ndad_created', '`created_at`');
CALL netdiag_add_index_if_missing('net_diag_notification_log', 'idx_ndnl_created', '`created_at`');

DROP PROCEDURE IF EXISTS netdiag_add_column_if_missing;
DROP PROCEDURE IF EXISTS netdiag_add_index_if_missing;
