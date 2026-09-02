-- F3: indices de obs_span.
-- Sustituye los indices de una sola columna por los compuestos que usan las consultas reales
-- e indexa http_route por prefijo (la columna es VARCHAR(500) y no cabe entera en un indice).
-- Idempotente: hasta F9 estas estructuras las crea tambien ddl-auto=update.

DROP PROCEDURE IF EXISTS obs_add_index_if_missing;
DROP PROCEDURE IF EXISTS obs_drop_index_if_exists;

DELIMITER $$

CREATE PROCEDURE obs_add_index_if_missing(
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

CREATE PROCEDURE obs_drop_index_if_exists(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @ddl = CONCAT('DROP INDEX `', p_index, '` ON `', p_table, '`');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL obs_add_index_if_missing('obs_span', 'idx_obs_span_trace_parent', '`trace_id`,`parent_span_id`');
CALL obs_add_index_if_missing('obs_span', 'idx_obs_span_root_start', '`parent_span_id`,`start_epoch_ms`');
CALL obs_add_index_if_missing('obs_span', 'idx_obs_span_start', '`start_epoch_ms`');
CALL obs_add_index_if_missing('obs_span', 'idx_obs_span_session', '`session_id`');
CALL obs_add_index_if_missing('obs_span', 'idx_obs_span_route', '`http_route`(96),`start_epoch_ms`');

CALL obs_drop_index_if_exists('obs_span', 'idx_obs_span_trace');
CALL obs_drop_index_if_exists('obs_span', 'idx_obs_span_parent');

DROP PROCEDURE IF EXISTS obs_add_index_if_missing;
DROP PROCEDURE IF EXISTS obs_drop_index_if_exists;
