ALTER TABLE provisioning_v2_operation
    MODIFY COLUMN subscription_id INT NULL,
    ADD COLUMN phase VARCHAR(40) NOT NULL DEFAULT 'PROVISIONING',
    ADD COLUMN operator_id BIGINT NULL,
    ADD COLUMN operator_username VARCHAR(128) NULL,
    ADD COLUMN registration_request_key VARCHAR(64) NULL,
    ADD INDEX idx_provisioning_v2_phase_due (phase, state, next_attempt_at, lease_until),
    ADD INDEX idx_provisioning_v2_operator (environment, operator_id, state),
    ADD UNIQUE INDEX uk_provisioning_v2_operator_request (environment, operator_id, registration_request_key);

CREATE TABLE IF NOT EXISTS provisioning_v2_operator_lock (
    operator_id BIGINT NOT NULL PRIMARY KEY,
    environment VARCHAR(32) NOT NULL,
    operation_id VARCHAR(64) NOT NULL UNIQUE,
    created_at_epoch_ms BIGINT NOT NULL,
    INDEX idx_provisioning_v2_operator_lock_operation (environment, operation_id)
);
