CREATE TABLE IF NOT EXISTS olt_provisioning_v2_onu_operation (
    sn VARCHAR(64) PRIMARY KEY,
    operation_id VARCHAR(64) NOT NULL UNIQUE,
    caller_env VARCHAR(32) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    external_id VARCHAR(128),
    board INT,
    port INT,
    ont_id INT,
    stage VARCHAR(16) NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    updated_at_epoch_ms BIGINT NOT NULL
);
