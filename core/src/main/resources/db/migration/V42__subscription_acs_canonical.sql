-- F8: subscription_acs pasa a ser el registro canonico del vinculo ACS.
-- 1) Backfill de las columnas heredadas de subscription hacia subscription_acs, para que
--    servicehealth pueda dejar de leer subscription.tr069_device_id / tr069_last_error.
-- 2) FK real entre subscription.fiber_onu_sn y onu.sn, que hoy solo existe como relacion JPA.
-- Idempotente y no destructiva: no borra columnas ni filas.

INSERT INTO subscription_acs (subscription_id, genieacs_device_id, last_error, provision_status, ssid_24, ssid_5, updated_at)
SELECT s.id, s.tr069_device_id, LEFT(s.tr069_last_error, 500), s.tr069_provision_status, s.wifi_ssid_24, s.wifi_ssid_5, NOW()
FROM subscription s
LEFT JOIN subscription_acs a ON a.subscription_id = s.id
WHERE a.subscription_id IS NULL
  AND s.tr069_device_id IS NOT NULL
  AND s.tr069_device_id <> '';

UPDATE subscription_acs a
JOIN subscription s ON s.id = a.subscription_id
SET a.genieacs_device_id = s.tr069_device_id,
    a.updated_at = NOW()
WHERE (a.genieacs_device_id IS NULL OR a.genieacs_device_id = '')
  AND s.tr069_device_id IS NOT NULL
  AND s.tr069_device_id <> '';

UPDATE subscription_acs a
JOIN subscription s ON s.id = a.subscription_id
SET a.last_error = LEFT(s.tr069_last_error, 500),
    a.updated_at = NOW()
WHERE (a.last_error IS NULL OR a.last_error = '')
  AND s.tr069_last_error IS NOT NULL
  AND s.tr069_last_error <> '';

DROP PROCEDURE IF EXISTS acs_link_fiber_onu_fk;

DELIMITER $$

CREATE PROCEDURE acs_link_fiber_onu_fk()
BEGIN
    DECLARE v_column VARCHAR(64) DEFAULT NULL;
    DECLARE v_orphans INT DEFAULT 0;

    -- La columna la genero Hibernate sin @JoinColumn: se resuelve en vez de asumirse.
    SELECT COLUMN_NAME INTO v_column
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription'
      AND COLUMN_NAME LIKE 'fiber\_onu%'
    ORDER BY COLUMN_NAME
    LIMIT 1;

    IF v_column IS NULL THEN
        LEAVE_PROC: BEGIN END;
    ELSE
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription'
              AND INDEX_NAME = 'idx_subscription_fiber_onu_sn'
        ) THEN
            SET @ddl = CONCAT('CREATE INDEX `idx_subscription_fiber_onu_sn` ON `subscription` (`', v_column, '`)');
            PREPARE stmt FROM @ddl;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        SET @cnt = CONCAT(
            'SELECT COUNT(*) INTO @orphans FROM subscription s LEFT JOIN onu o ON o.sn = s.`', v_column,
            '` WHERE s.`', v_column, '` IS NOT NULL AND o.sn IS NULL'
        );
        PREPARE stmt FROM @cnt;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SET v_orphans = IFNULL(@orphans, 1);

        -- Con huerfanos la FK no se crea: ese vinculo se repara en provision, no en una migracion.
        IF v_orphans = 0 AND NOT EXISTS (
            SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'subscription'
              AND CONSTRAINT_NAME = 'fk_subscription_fiber_onu'
        ) THEN
            SET @ddl = CONCAT(
                'ALTER TABLE `subscription` ADD CONSTRAINT `fk_subscription_fiber_onu` FOREIGN KEY (`',
                v_column, '`) REFERENCES `onu` (`sn`)'
            );
            PREPARE stmt FROM @ddl;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END IF;
END$$

DELIMITER ;

CALL acs_link_fiber_onu_fk();

DROP PROCEDURE IF EXISTS acs_link_fiber_onu_fk;
