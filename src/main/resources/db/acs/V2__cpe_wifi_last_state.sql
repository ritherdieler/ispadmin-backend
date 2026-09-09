-- ACS last-state WiFi snapshot for Inform push (series live in Core).
ALTER TABLE cpe_record
    ADD COLUMN wifi_snapshot_json TEXT NULL,
    ADD COLUMN wifi_associated_2g INT NULL,
    ADD COLUMN wifi_associated_5g INT NULL,
    ADD COLUMN wifi_associated_total INT NULL,
    ADD COLUMN wifi_observed_at DATETIME(6) NULL,
    ADD COLUMN wifi_quality_status VARCHAR(32) NULL;
