ALTER TABLE subscription
    ADD COLUMN wifi_ssid_24 VARCHAR(32) NULL,
    ADD COLUMN wifi_ssid_5 VARCHAR(32) NULL,
    ADD COLUMN wifi_password_24_enc VARCHAR(512) NULL,
    ADD COLUMN wifi_password_5_enc VARCHAR(512) NULL,
    ADD COLUMN tr069_provision_status VARCHAR(32) NULL,
    ADD COLUMN tr069_device_id VARCHAR(128) NULL,
    ADD COLUMN tr069_last_error VARCHAR(500) NULL;

UPDATE subscription
SET tr069_provision_status = 'NA'
WHERE tr069_provision_status IS NULL;
