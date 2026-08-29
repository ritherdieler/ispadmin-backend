ALTER TABLE subscription_traffic_sample
    MODIFY rx_bytes_delta BIGINT NULL,
    MODIFY tx_bytes_delta BIGINT NULL,
    MODIFY avg_mbps_down DOUBLE NULL,
    MODIFY avg_mbps_up DOUBLE NULL,
    ADD COLUMN collected_at DATETIME NULL AFTER bucket_start,
    ADD COLUMN interval_seconds INT NULL AFTER collected_at,
    ADD COLUMN sample_status VARCHAR(24) NOT NULL DEFAULT 'OK' AFTER interval_seconds,
    ADD COLUMN error_reason VARCHAR(255) NULL AFTER sample_status,
    ADD COLUMN source_run_id BIGINT NULL AFTER error_reason,
    ADD COLUMN queue_id VARCHAR(96) NULL AFTER source_run_id,
    ADD COLUMN queue_name VARCHAR(160) NULL AFTER queue_id,
    ADD COLUMN plan_download_mbps INT NULL AFTER queue_name,
    ADD COLUMN plan_upload_mbps INT NULL AFTER plan_download_mbps;

UPDATE subscription_traffic_sample
SET collected_at = bucket_start,
    interval_seconds = 300
WHERE collected_at IS NULL;

UPDATE subscription_traffic_sample
SET sample_status = 'OK'
WHERE sample_status IS NULL OR TRIM(sample_status) = '';

ALTER TABLE subscription_traffic_sample
    MODIFY sample_status VARCHAR(24) NOT NULL DEFAULT 'OK' COMMENT 'bandwidth-v1-compatible';

ALTER TABLE subscription_traffic_hourly
    ADD COLUMN avg_mbps_down DOUBLE NOT NULL DEFAULT 0,
    ADD COLUMN avg_mbps_up DOUBLE NOT NULL DEFAULT 0,
    ADD COLUMN expected_sample_count INT NOT NULL DEFAULT 0,
    ADD COLUMN coverage_pct DOUBLE NOT NULL DEFAULT 0,
    ADD COLUMN plan_download_mbps INT NULL,
    ADD COLUMN plan_upload_mbps INT NULL,
    ADD COLUMN utilization_down_pct DOUBLE NULL,
    ADD COLUMN utilization_up_pct DOUBLE NULL,
    ADD COLUMN seconds_over_80 INT NOT NULL DEFAULT 0,
    ADD COLUMN seconds_over_90 INT NOT NULL DEFAULT 0,
    ADD COLUMN seconds_over_95 INT NOT NULL DEFAULT 0;

ALTER TABLE subscription_traffic_daily
    ADD COLUMN avg_mbps_down DOUBLE NOT NULL DEFAULT 0,
    ADD COLUMN avg_mbps_up DOUBLE NOT NULL DEFAULT 0,
    ADD COLUMN sample_count INT NOT NULL DEFAULT 0,
    ADD COLUMN expected_sample_count INT NOT NULL DEFAULT 0,
    ADD COLUMN coverage_pct DOUBLE NOT NULL DEFAULT 0,
    ADD COLUMN plan_download_mbps INT NULL,
    ADD COLUMN plan_upload_mbps INT NULL,
    ADD COLUMN utilization_down_pct DOUBLE NULL,
    ADD COLUMN utilization_up_pct DOUBLE NULL,
    ADD COLUMN seconds_over_80 INT NOT NULL DEFAULT 0,
    ADD COLUMN seconds_over_90 INT NOT NULL DEFAULT 0,
    ADD COLUMN seconds_over_95 INT NOT NULL DEFAULT 0;

ALTER TABLE subscription_traffic_monthly
    ADD COLUMN avg_mbps_down DOUBLE NOT NULL DEFAULT 0,
    ADD COLUMN avg_mbps_up DOUBLE NOT NULL DEFAULT 0,
    ADD COLUMN sample_count INT NOT NULL DEFAULT 0,
    ADD COLUMN expected_sample_count INT NOT NULL DEFAULT 0,
    ADD COLUMN coverage_pct DOUBLE NOT NULL DEFAULT 0,
    ADD COLUMN plan_download_mbps INT NULL,
    ADD COLUMN plan_upload_mbps INT NULL,
    ADD COLUMN utilization_down_pct DOUBLE NULL,
    ADD COLUMN utilization_up_pct DOUBLE NULL;

CREATE TABLE subscription_traffic_five_minute (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id INT NOT NULL,
    host_device_id INT NOT NULL,
    bucket_start DATETIME NOT NULL,
    rx_bytes_total BIGINT NOT NULL DEFAULT 0,
    tx_bytes_total BIGINT NOT NULL DEFAULT 0,
    avg_mbps_down DOUBLE NOT NULL DEFAULT 0,
    avg_mbps_up DOUBLE NOT NULL DEFAULT 0,
    max_mbps_down DOUBLE NOT NULL DEFAULT 0,
    max_mbps_up DOUBLE NOT NULL DEFAULT 0,
    p95_mbps_down DOUBLE NOT NULL DEFAULT 0,
    p95_mbps_up DOUBLE NOT NULL DEFAULT 0,
    sample_count INT NOT NULL DEFAULT 0,
    expected_sample_count INT NOT NULL DEFAULT 0,
    coverage_pct DOUBLE NOT NULL DEFAULT 0,
    plan_download_mbps INT NULL,
    plan_upload_mbps INT NULL,
    utilization_down_pct DOUBLE NULL,
    utilization_up_pct DOUBLE NULL,
    seconds_over_80 INT NOT NULL DEFAULT 0,
    seconds_over_90 INT NOT NULL DEFAULT 0,
    seconds_over_95 INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_traffic_5m_sub_bucket UNIQUE (subscription_id, bucket_start),
    CONSTRAINT fk_traffic_5m_subscription FOREIGN KEY (subscription_id) REFERENCES subscription (id),
    INDEX idx_traffic_5m_bucket (bucket_start),
    INDEX idx_traffic_5m_router_bucket (host_device_id, bucket_start)
);

CREATE TABLE traffic_source_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    host_device_id INT NOT NULL,
    started_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    status VARCHAR(24) NOT NULL,
    expected_count INT NOT NULL DEFAULT 0,
    matched_count INT NOT NULL DEFAULT 0,
    written_count INT NOT NULL DEFAULT 0,
    missing_count INT NOT NULL DEFAULT 0,
    invalid_count INT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    lag_seconds BIGINT NOT NULL DEFAULT 0,
    error_message VARCHAR(500) NULL,
    INDEX idx_traffic_source_run_router_time (host_device_id, started_at),
    INDEX idx_traffic_source_run_started (started_at)
);

CREATE TABLE traffic_anomaly_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dedupe_key VARCHAR(220) NOT NULL,
    anomaly_type VARCHAR(40) NOT NULL,
    event_status VARCHAR(16) NOT NULL,
    subscription_id INT NULL,
    host_device_id INT NULL,
    started_at DATETIME NOT NULL,
    ended_at DATETIME NULL,
    last_evaluated_at DATETIME NOT NULL,
    baseline_value DOUBLE NULL,
    observed_value DOUBLE NULL,
    deviation_value DOUBLE NULL,
    coverage_pct DOUBLE NOT NULL DEFAULT 0,
    confidence DOUBLE NOT NULL DEFAULT 0,
    rule_version VARCHAR(48) NOT NULL,
    evidence_json TEXT NULL,
    CONSTRAINT uk_traffic_anomaly_dedupe UNIQUE (dedupe_key),
    CONSTRAINT fk_traffic_anomaly_subscription FOREIGN KEY (subscription_id) REFERENCES subscription (id),
    INDEX idx_traffic_anomaly_status_time (event_status, started_at),
    INDEX idx_traffic_anomaly_subscription_time (subscription_id, started_at),
    INDEX idx_traffic_anomaly_router_time (host_device_id, started_at)
);
