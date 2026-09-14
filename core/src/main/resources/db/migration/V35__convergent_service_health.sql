-- Diagnostic pilot only. Apply explicitly; Flyway is not enabled.
-- No ALTER/DROP of existing production tables; safe to apply twice.

CREATE TABLE IF NOT EXISTS `olt_mgr_onu_optical_sample` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `subscription_id` INT NULL,
  `onu_id` BIGINT NOT NULL,
  `onu_sn` VARCHAR(255) NOT NULL,
  `olt_id` BIGINT NOT NULL,
  `board` INT NOT NULL,
  `port` INT NOT NULL,
  `onu_index` INT NOT NULL,
  `observed_at` DATETIME(6) NOT NULL,
  `collected_at` DATETIME(6) NOT NULL,
  `onu_rx_dbm` DOUBLE NULL,
  `onu_tx_dbm` DOUBLE NULL,
  `olt_rx_dbm` DOUBLE NULL,
  `temperature_c` DOUBLE NULL,
  `distance_m` INT NULL,
  `bias_ma` DOUBLE NULL,
  `voltage_v` DOUBLE NULL,
  `quality_status` VARCHAR(32) NOT NULL,
  `source_run_id` BIGINT NULL,
  `error_reason` VARCHAR(255) NULL,
  INDEX `idx_sh_optical_sub_time` (`subscription_id`, `observed_at`),
  INDEX `idx_sh_optical_onu_time` (`onu_id`, `observed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `service_onu_state_event` (
  `source_event_id` BIGINT NULL,
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `subscription_id` INT NULL,
  `onu_id` BIGINT NOT NULL,
  `onu_sn` VARCHAR(255) NOT NULL,
  `olt_id` BIGINT NOT NULL,
  `board` INT NOT NULL,
  `port` INT NOT NULL,
  `previous_state` VARCHAR(255) NULL,
  `state` VARCHAR(255) NOT NULL,
  `cause` VARCHAR(255) NULL,
  `observed_at` DATETIME(6) NOT NULL,
  `collected_at` DATETIME(6) NOT NULL,
  `source` VARCHAR(255) NOT NULL,
  CONSTRAINT `uk_sh_state_source` UNIQUE (`source`, `source_event_id`),
  INDEX `idx_sh_state_sub_time` (`subscription_id`, `observed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `acs_wifi_count_sample` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `subscription_id` INT NOT NULL,
  `device_id` VARCHAR(128) NOT NULL,
  `inform_at` DATETIME(6) NOT NULL,
  `observed_at` DATETIME(6) NULL,
  `collected_at` DATETIME(6) NOT NULL,
  `associated_device_count` INT NULL,
  `associated2g` INT NULL,
  `associated5g` INT NULL,
  `lan_device_count` INT NULL,
  `quality_status` VARCHAR(32) NOT NULL,
  `source_run_id` BIGINT NULL,
  `error_reason` VARCHAR(255) NULL,
  CONSTRAINT `uk_sh_wifi_reading` UNIQUE (`device_id`, `subscription_id`, `inform_at`),
  INDEX `idx_sh_wifi_sub_time` (`subscription_id`, `observed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `acs_wifi_station_sample` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `count_sample_id` BIGINT NOT NULL,
  `subscription_id` INT NOT NULL,
  `station_key` VARCHAR(64) NOT NULL,
  `band` VARCHAR(8) NOT NULL,
  `observed_at` DATETIME(6) NOT NULL,
  `collected_at` DATETIME(6) NOT NULL,
  `rssi` DOUBLE NULL,
  `snr` DOUBLE NULL,
  `noise` DOUBLE NULL,
  `rx_rate` DOUBLE NULL,
  `tx_rate` DOUBLE NULL,
  `packets_tx` BIGINT NULL,
  `packets_rx` BIGINT NULL,
  `quality_status` VARCHAR(32) NOT NULL,
  CONSTRAINT `uk_sh_station_reading` UNIQUE (`count_sample_id`, `station_key`, `band`),
  INDEX `idx_sh_station_sub_time` (`subscription_id`, `observed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `acs_wifi_status_current` (
  `model` VARCHAR(64) NULL,
  `subscription_id` INT NOT NULL PRIMARY KEY,
  `device_id` VARCHAR(128) NOT NULL,
  `count_sample_id` BIGINT NULL,
  `inform_at` DATETIME(6) NULL,
  `observed_at` DATETIME(6) NULL,
  `associated_device_count` INT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `quality_status` VARCHAR(32) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `capability_profile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `manufacturer` VARCHAR(64) NOT NULL,
  `model` VARCHAR(64) NOT NULL,
  `firmware` VARCHAR(64) NOT NULL,
  `wifi_count` BIT(1) NOT NULL,
  `wifi_signal` BIT(1) NOT NULL,
  `station_bytes` BIT(1) NOT NULL,
  `verified_at` DATETIME(6) NULL,
  `version` VARCHAR(255) NOT NULL,
  CONSTRAINT `uk_sh_read_profile` UNIQUE (`manufacturer`, `model`, `firmware`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `telemetry_source_run` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `source` VARCHAR(255) NOT NULL,
  `equipment_key` VARCHAR(255) NOT NULL,
  `domain_run_id` BIGINT NULL,
  `started_at` DATETIME(6) NOT NULL,
  `completed_at` DATETIME(6) NULL,
  `quality_status` VARCHAR(32) NOT NULL,
  `read_count` INT NOT NULL,
  `written_count` INT NOT NULL,
  `missing_count` INT NOT NULL,
  `unmapped_count` INT NOT NULL,
  `unsupported_count` INT NOT NULL,
  `error_count` INT NOT NULL,
  `lag_seconds` BIGINT NOT NULL,
  `error_reason` VARCHAR(255) NULL,
  INDEX `idx_sh_run_source_time` (`source`, `equipment_key`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `identity_link` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `subscription_id` INT NOT NULL,
  `kind` VARCHAR(32) NOT NULL,
  `identity_value` VARCHAR(160) NOT NULL,
  `valid_from` DATETIME(6) NOT NULL,
  `valid_to` DATETIME(6) NULL,
  `source` VARCHAR(255) NOT NULL,
  `confidence` DOUBLE NOT NULL,
  `verified_at` DATETIME(6) NOT NULL,
  INDEX `idx_sh_identity_sub` (`subscription_id`, `valid_to`),
  INDEX `idx_sh_identity_value` (`kind`, `identity_value`, `valid_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `service_identity_conflict` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `conflict_key` VARCHAR(200) NOT NULL UNIQUE,
  `kind` VARCHAR(255) NOT NULL,
  `identity_value` VARCHAR(255) NOT NULL,
  `subscription_ids_json` TEXT NOT NULL,
  `status` VARCHAR(255) NOT NULL,
  `reason` VARCHAR(255) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `resolved_at` DATETIME(6) NULL,
  `resolved_by` INT NULL,
  `resolution` VARCHAR(500) NULL,
  INDEX `idx_sh_conflict_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `service_health_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `subscription_id` INT NOT NULL,
  `diagnosis_code` VARCHAR(255) NOT NULL,
  `event_status` VARCHAR(255) NOT NULL,
  `confidence` VARCHAR(32) NOT NULL,
  `observed_at` DATETIME(6) NOT NULL,
  `evaluated_at` DATETIME(6) NOT NULL,
  `ended_at` DATETIME(6) NULL,
  `rule_version` VARCHAR(255) NOT NULL,
  `diagnosis_json` TEXT NOT NULL,
  `identity_snapshot_json` TEXT NOT NULL,
  `suppressing_incident_id` BIGINT NULL,
  INDEX `idx_sh_event_sub_time` (`subscription_id`, `observed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `service_health_current` (
  `subscription_id` INT NOT NULL PRIMARY KEY,
  `evaluated_at` DATETIME(6) NOT NULL,
  `summary_json` TEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `evidence_link` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `health_event_id` BIGINT NOT NULL,
  `source` VARCHAR(255) NOT NULL,
  `reference_id` VARCHAR(255) NOT NULL,
  `observed_at` DATETIME(6) NULL,
  `summary_json` TEXT NOT NULL,
  CONSTRAINT `uk_sh_evidence` UNIQUE (`health_event_id`, `source`, `reference_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `service_health_cursor` (
  `cursor_key` VARCHAR(160) NOT NULL PRIMARY KEY,
  `observed_at` DATETIME(6) NULL,
  `reference_id` BIGINT NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `service_traffic_evidence` (
  `event_id` BIGINT NOT NULL PRIMARY KEY,
  `subscription_id` INT NULL,
  `router_id` INT NULL,
  `event_status` VARCHAR(255) NOT NULL,
  `anomaly_type` VARCHAR(255) NOT NULL,
  `observed_at` DATETIME(6) NOT NULL,
  `coverage_pct` DOUBLE NOT NULL,
  `confidence` DOUBLE NOT NULL,
  `evidence_json` TEXT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `service_incident_subscription` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `incident_id` BIGINT NOT NULL,
  `subscription_id` INT NOT NULL,
  `health_event_id` BIGINT NULL,
  `state` VARCHAR(255) NOT NULL,
  `first_seen_at` DATETIME(6) NOT NULL,
  `last_seen_at` DATETIME(6) NOT NULL,
  `recovered_at` DATETIME(6) NULL,
  `scope_json` TEXT NOT NULL,
  CONSTRAINT `uk_sh_affected` UNIQUE (`incident_id`, `subscription_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `service_remote_action` (
  `acs_device_id` VARCHAR(128) NULL,
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `subscription_id` INT NOT NULL,
  `actor_id` INT NOT NULL,
  `request_key` VARCHAR(128) NOT NULL,
  `device_key` VARCHAR(160) NOT NULL,
  `action` VARCHAR(255) NOT NULL,
  `status` VARCHAR(255) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `completed_at` DATETIME(6) NULL,
  `network_status` VARCHAR(255) NULL,
  `wifi_status` VARCHAR(255) NULL,
  `network_channel` VARCHAR(255) NULL,
  `task_id` VARCHAR(255) NULL,
  `error_reason` VARCHAR(200) NULL,
  `request_digest` VARCHAR(64) NOT NULL,
  `confirmation_json` TEXT NULL,
  CONSTRAINT `uk_sh_action_request` UNIQUE (`actor_id`, `request_key`),
  INDEX `idx_sh_action_device_time` (`device_key`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT IGNORE INTO service_health_cursor (cursor_key, observed_at, reference_id, updated_at, version) VALUES
('acs-watcher', NULL, 0, UTC_TIMESTAMP(6), 0),
('traffic-consumer', NULL, 0, UTC_TIMESTAMP(6), 0),
('evaluation', NULL, 0, UTC_TIMESTAMP(6), 0),
('actions', NULL, 0, UTC_TIMESTAMP(6), 0),
('blast-radius', NULL, 0, UTC_TIMESTAMP(6), 0),
('olt-events', UTC_TIMESTAMP(6), 0, UTC_TIMESTAMP(6), 0);
