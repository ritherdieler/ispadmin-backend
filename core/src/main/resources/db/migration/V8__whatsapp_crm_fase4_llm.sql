CREATE TABLE IF NOT EXISTS crm_integration_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(128) NOT NULL,
    value_ciphertext VARCHAR(4000) NULL,
    value_plain VARCHAR(1000) NULL,
    sensitive_flag BIT(1) NOT NULL DEFAULT 0,
    updated_by VARCHAR(128) NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_crm_integration_setting_key (setting_key)
);

ALTER TABLE crm_conversation
    ADD COLUMN handoff_summary VARCHAR(4000) NULL;
