CREATE TABLE subscription_traffic_sample (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id INT NOT NULL,
    host_device_id INT NOT NULL,
    bucket_start DATETIME NOT NULL,
    rx_bytes_delta BIGINT NOT NULL DEFAULT 0,
    tx_bytes_delta BIGINT NOT NULL DEFAULT 0,
    avg_mbps_down DOUBLE NOT NULL DEFAULT 0,
    avg_mbps_up DOUBLE NOT NULL DEFAULT 0,
    counter_reset TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT uk_traffic_sample_sub_bucket UNIQUE (subscription_id, bucket_start),
    CONSTRAINT fk_traffic_sample_subscription FOREIGN KEY (subscription_id) REFERENCES subscription (id),
    INDEX idx_traffic_sample_bucket (bucket_start),
    INDEX idx_traffic_sample_sub_bucket (subscription_id, bucket_start)
);

CREATE TABLE subscription_traffic_hourly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id INT NOT NULL,
    bucket_start DATETIME NOT NULL,
    rx_bytes_total BIGINT NOT NULL DEFAULT 0,
    tx_bytes_total BIGINT NOT NULL DEFAULT 0,
    max_mbps_down DOUBLE NOT NULL DEFAULT 0,
    max_mbps_up DOUBLE NOT NULL DEFAULT 0,
    p95_mbps_down DOUBLE NOT NULL DEFAULT 0,
    p95_mbps_up DOUBLE NOT NULL DEFAULT 0,
    sample_count INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_traffic_hourly_sub_bucket UNIQUE (subscription_id, bucket_start),
    CONSTRAINT fk_traffic_hourly_subscription FOREIGN KEY (subscription_id) REFERENCES subscription (id),
    INDEX idx_traffic_hourly_bucket (bucket_start),
    INDEX idx_traffic_hourly_sub_bucket (subscription_id, bucket_start)
);

CREATE TABLE subscription_traffic_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id INT NOT NULL,
    bucket_start DATE NOT NULL,
    rx_bytes_total BIGINT NOT NULL DEFAULT 0,
    tx_bytes_total BIGINT NOT NULL DEFAULT 0,
    max_mbps_down DOUBLE NOT NULL DEFAULT 0,
    max_mbps_up DOUBLE NOT NULL DEFAULT 0,
    p95_mbps_down DOUBLE NOT NULL DEFAULT 0,
    p95_mbps_up DOUBLE NOT NULL DEFAULT 0,
    active_hours INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_traffic_daily_sub_bucket UNIQUE (subscription_id, bucket_start),
    CONSTRAINT fk_traffic_daily_subscription FOREIGN KEY (subscription_id) REFERENCES subscription (id),
    INDEX idx_traffic_daily_bucket (bucket_start),
    INDEX idx_traffic_daily_sub_bucket (subscription_id, bucket_start)
);

CREATE TABLE subscription_traffic_monthly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id INT NOT NULL,
    month_key CHAR(7) NOT NULL,
    rx_bytes_total BIGINT NOT NULL DEFAULT 0,
    tx_bytes_total BIGINT NOT NULL DEFAULT 0,
    max_mbps_down DOUBLE NOT NULL DEFAULT 0,
    max_mbps_up DOUBLE NOT NULL DEFAULT 0,
    p95_mbps_down DOUBLE NOT NULL DEFAULT 0,
    p95_mbps_up DOUBLE NOT NULL DEFAULT 0,
    active_days INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_traffic_monthly_sub_month UNIQUE (subscription_id, month_key),
    CONSTRAINT fk_traffic_monthly_subscription FOREIGN KEY (subscription_id) REFERENCES subscription (id),
    INDEX idx_traffic_monthly_month (month_key)
);

CREATE TABLE subscription_traffic_counter_state (
    subscription_id INT PRIMARY KEY,
    host_device_id INT NOT NULL,
    last_rx_bytes BIGINT NOT NULL DEFAULT 0,
    last_tx_bytes BIGINT NOT NULL DEFAULT 0,
    last_router_uptime_seconds BIGINT NULL,
    last_polled_at DATETIME NULL,
    CONSTRAINT fk_traffic_counter_subscription FOREIGN KEY (subscription_id) REFERENCES subscription (id),
    INDEX idx_traffic_counter_host (host_device_id)
);
