CREATE TABLE IF NOT EXISTS provisioning_v2_operation (
    operation_id VARCHAR(64) NOT NULL PRIMARY KEY,
    environment VARCHAR(32) NOT NULL,
    subscription_id INT NOT NULL,
    serial VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL,
    operation_json LONGTEXT NOT NULL,
    state VARCHAR(32) NOT NULL,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    lease_token VARCHAR(64) NULL,
    lease_until BIGINT NOT NULL DEFAULT 0,
    next_attempt_at BIGINT NOT NULL DEFAULT 0,
    INDEX idx_provisioning_v2_due (state, next_attempt_at, lease_until),
    INDEX idx_provisioning_v2_subscription (environment, subscription_id)
);

CREATE TABLE IF NOT EXISTS provisioning_v2_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operation_id VARCHAR(64) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    delivered BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_provisioning_v2_outbox (delivered, id)
);

CREATE TABLE IF NOT EXISTS provisioning_v2_resource (
    operation_id VARCHAR(64) NOT NULL,
    resource_key VARCHAR(128) NOT NULL,
    snapshot_cipher LONGTEXT NOT NULL,
    PRIMARY KEY (operation_id, resource_key)
);
