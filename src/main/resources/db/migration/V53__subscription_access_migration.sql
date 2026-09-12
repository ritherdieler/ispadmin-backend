CREATE TABLE IF NOT EXISTS `subscription_access_migration` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `subscription_id` INT NOT NULL,
  `stage` VARCHAR(32) NOT NULL,
  `attempt` INT NOT NULL DEFAULT 1,
  `previous_ip` VARCHAR(45) NULL,
  `previous_vlan` VARCHAR(10) NULL,
  `previous_queue_target` VARCHAR(45) NULL,
  `pppoe_username` VARCHAR(64) NULL,
  `quarantine_until` DATETIME NULL,
  `failure_reason` VARCHAR(500) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_access_migration_subscription` (`subscription_id`),
  KEY `idx_access_migration_stage` (`stage`),
  KEY `idx_access_migration_quarantine` (`stage`, `quarantine_until`),
  CONSTRAINT `fk_access_migration_subscription`
    FOREIGN KEY (`subscription_id`) REFERENCES `subscription` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
