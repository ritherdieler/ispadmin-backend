ALTER TABLE subscription
    ADD COLUMN mikrotik_provision_status VARCHAR(32) NULL,
    ADD COLUMN olt_provision_status VARCHAR(32) NULL,
    ADD COLUMN provision_attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN provision_next_attempt_at DATETIME NULL,
    ADD COLUMN provision_last_error VARCHAR(500) NULL;

UPDATE subscription
SET mikrotik_provision_status = 'COMPLETE',
    olt_provision_status = CASE
        WHEN installation_type = 'FIBER' THEN 'COMPLETE'
        ELSE 'NA'
    END
WHERE mikrotik_provision_status IS NULL;

CREATE INDEX idx_subscription_provision_retry
    ON subscription (mikrotik_provision_status, olt_provision_status, provision_next_attempt_at);
