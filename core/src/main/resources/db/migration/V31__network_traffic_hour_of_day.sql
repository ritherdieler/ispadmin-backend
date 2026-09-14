CREATE TABLE network_traffic_hour_of_day (
    bucket_date DATE NOT NULL,
    hour_of_day TINYINT NOT NULL,
    rx_bytes_total BIGINT NOT NULL DEFAULT 0,
    tx_bytes_total BIGINT NOT NULL DEFAULT 0,
    active_subscriptions INT NOT NULL DEFAULT 0,
    PRIMARY KEY (bucket_date, hour_of_day),
    INDEX idx_network_traffic_hour_date (bucket_date)
);
