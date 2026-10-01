ALTER TABLE provisioning_v2_event
    ADD COLUMN created_at_epoch_ms BIGINT NOT NULL DEFAULT 0,
    ADD INDEX idx_provisioning_v2_event_retention (delivered, created_at_epoch_ms);
