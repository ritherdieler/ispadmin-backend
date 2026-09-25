CREATE TABLE IF NOT EXISTS acs_onboarding_v2_task (
    operation_id VARCHAR(64) NOT NULL,
    action VARCHAR(24) NOT NULL,
    sn VARCHAR(64) NOT NULL,
    device_id VARCHAR(256) NOT NULL,
    model VARCHAR(128) NOT NULL,
    firmware VARCHAR(256) NOT NULL,
    baseline_cipher LONGTEXT,
    desired_cipher LONGTEXT,
    genie_task_id VARCHAR(256),
    status VARCHAR(24) NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    updated_at_epoch_ms BIGINT NOT NULL,
    PRIMARY KEY (operation_id, action)
);
