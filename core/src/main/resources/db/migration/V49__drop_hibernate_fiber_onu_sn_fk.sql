-- V44 only dropped CONSTRAINT_NAME = fk_subscription_fiber_onu.
-- Staging/prod still had the Hibernate auto-named FK FKj3wtbty2hpt2essof46euiuv0
-- on subscription.fiber_onu_sn → onu(sn), which blocks FIBER alta when onu has no row.
-- Inventory SoT is olt_mgr_onu; fiber_onu_sn is a business key only.

DROP PROCEDURE IF EXISTS sot_drop_any_fiber_onu_sn_fk;

DELIMITER $$

CREATE PROCEDURE sot_drop_any_fiber_onu_sn_fk()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_name VARCHAR(64);
    DECLARE cur CURSOR FOR
        SELECT kcu.CONSTRAINT_NAME
        FROM information_schema.KEY_COLUMN_USAGE kcu
        WHERE kcu.TABLE_SCHEMA = DATABASE()
          AND kcu.TABLE_NAME = 'subscription'
          AND kcu.COLUMN_NAME = 'fiber_onu_sn'
          AND kcu.REFERENCED_TABLE_NAME = 'onu';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO v_name;
        IF done = 1 THEN
            LEAVE read_loop;
        END IF;
        SET @ddl = CONCAT('ALTER TABLE `subscription` DROP FOREIGN KEY `', v_name, '`');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE cur;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'subscription'
          AND INDEX_NAME = 'idx_subscription_fiber_onu_sn'
    ) THEN
        CREATE INDEX `idx_subscription_fiber_onu_sn` ON `subscription` (`fiber_onu_sn`);
    END IF;
END$$

DELIMITER ;

CALL sot_drop_any_fiber_onu_sn_fk();

DROP PROCEDURE IF EXISTS sot_drop_any_fiber_onu_sn_fk;
