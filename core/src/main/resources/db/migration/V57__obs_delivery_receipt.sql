CREATE TABLE IF NOT EXISTS obs_delivery_receipt (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    payload_hash VARCHAR(64) NOT NULL,
    issue_id BIGINT NULL,
    version BIGINT NULL
);
