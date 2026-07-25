ALTER TABLE whatsapp_message_log
    ADD COLUMN sent_at DATETIME NULL,
    ADD COLUMN delivered_at DATETIME NULL,
    ADD COLUMN read_at DATETIME NULL,
    ADD COLUMN failed_at DATETIME NULL,
    ADD COLUMN conversation_id VARCHAR(255) NULL,
    ADD COLUMN conversation_category VARCHAR(64) NULL,
    ADD COLUMN billable TINYINT(1) NULL,
    ADD COLUMN pricing_model VARCHAR(32) NULL,
    ADD COLUMN campaign_id VARCHAR(64) NULL,
    ADD COLUMN operator_username VARCHAR(128) NULL;

ALTER TABLE whatsapp_inbound_message
    ADD COLUMN button_reply_id VARCHAR(128) NULL,
    ADD COLUMN button_reply_title VARCHAR(512) NULL,
    ADD COLUMN media_id VARCHAR(255) NULL,
    ADD COLUMN media_mime_type VARCHAR(128) NULL,
    ADD COLUMN media_stored_path VARCHAR(1024) NULL,
    ADD COLUMN context_message_id VARCHAR(255) NULL,
    ADD COLUMN reply_to_log_id INT NULL;

CREATE TABLE IF NOT EXISTS whatsapp_phone_session (
    phone VARCHAR(20) NOT NULL PRIMARY KEY,
    service_window_expires_at DATETIME NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS whatsapp_account_event (
    id INT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    event_key VARCHAR(512) NOT NULL UNIQUE,
    template_name VARCHAR(255) NULL,
    template_id VARCHAR(64) NULL,
    severity VARCHAR(32) NULL,
    quality_score VARCHAR(32) NULL,
    payload_summary VARCHAR(4000) NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_wa_account_event_type (event_type),
    INDEX idx_wa_account_event_created (created_at)
);

CREATE TABLE IF NOT EXISTS whatsapp_synced_template (
    meta_template_id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(64) NULL,
    category VARCHAR(64) NULL,
    quality_score VARCHAR(32) NULL,
    language VARCHAR(16) NULL,
    synced_at DATETIME NOT NULL,
    INDEX idx_wa_synced_template_name (name)
);
