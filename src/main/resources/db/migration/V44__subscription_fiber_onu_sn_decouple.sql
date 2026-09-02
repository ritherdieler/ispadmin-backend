-- F2 SoT: subscription.fiber_onu_sn is only a business key (SN).
-- Inventory lives in olt_mgr_onu; drop the legacy FK to the onu table.

DROP PROCEDURE IF EXISTS sot_drop_fiber_onu_fk;

DELIMITER $$

CREATE PROCEDURE sot_drop_fiber_onu_fk()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'subscription'
          AND CONSTRAINT_NAME = 'fk_subscription_fiber_onu'
          AND CONSTRAINT_TYPE = 'FOREIGN KEY'
    ) THEN
        ALTER TABLE `subscription` DROP FOREIGN KEY `fk_subscription_fiber_onu`;
    END IF;

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

CALL sot_drop_fiber_onu_fk();

DROP PROCEDURE IF EXISTS sot_drop_fiber_onu_fk;
