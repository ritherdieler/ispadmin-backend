CREATE TABLE IF NOT EXISTS olt_activation_operation (
    sn VARCHAR(64) PRIMARY KEY,
    operation_id VARCHAR(36) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    request_cipher LONGTEXT NOT NULL,
    status_json LONGTEXT NOT NULL,
    external_id VARCHAR(128),
    stage VARCHAR(16) NOT NULL,
    lease_until BIGINT NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    event_published BOOLEAN NOT NULL DEFAULT FALSE
);
