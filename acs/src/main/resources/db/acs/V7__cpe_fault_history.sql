CREATE TABLE IF NOT EXISTS acs_fault_history (
    device_id VARCHAR(256) NOT NULL,
    fault_id VARCHAR(256) NOT NULL,
    sn VARCHAR(64) NOT NULL,
    channel VARCHAR(256),
    task_id VARCHAR(256),
    code VARCHAR(64) NOT NULL,
    description VARCHAR(256) NOT NULL,
    parameters_json TEXT NOT NULL,
    retries INT,
    occurred_at VARCHAR(40),
    first_seen_at VARCHAR(40) NOT NULL,
    last_seen_at VARCHAR(40) NOT NULL,
    resolved_at VARCHAR(40),
    PRIMARY KEY (device_id, fault_id),
    INDEX idx_acs_fault_history_device_seen (device_id, first_seen_at)
);
