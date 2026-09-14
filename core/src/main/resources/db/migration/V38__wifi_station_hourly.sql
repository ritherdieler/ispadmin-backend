CREATE TABLE IF NOT EXISTS `acs_wifi_station_hourly` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `subscription_id` INT NOT NULL,
  `station_key` VARCHAR(64) NOT NULL,
  `band` VARCHAR(8) NOT NULL,
  `bucket_start` DATETIME(6) NOT NULL,
  `rssi_min` DOUBLE NULL,
  `rssi_avg` DOUBLE NULL,
  `rssi_max` DOUBLE NULL,
  `snr_min` DOUBLE NULL,
  `snr_avg` DOUBLE NULL,
  `sample_count` INT NOT NULL,
  `display_name` VARCHAR(64) NULL,
  CONSTRAINT `uk_sh_station_hourly` UNIQUE (`subscription_id`, `station_key`, `band`, `bucket_start`),
  INDEX `idx_sh_station_hourly_sub_time` (`subscription_id`, `bucket_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS `acs_wifi_aggregation_watermark` (
  `layer` VARCHAR(32) NOT NULL PRIMARY KEY,
  `consolidated_through` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
