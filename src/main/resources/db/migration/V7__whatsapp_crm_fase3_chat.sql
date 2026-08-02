ALTER TABLE whatsapp_message_log
    ADD COLUMN reply_to_log_id INT NULL,
    ADD COLUMN media_meta_id VARCHAR(255) NULL,
    ADD COLUMN media_mime_type VARCHAR(128) NULL,
    ADD COLUMN media_stored_path VARCHAR(1024) NULL,
    ADD COLUMN media_filename VARCHAR(255) NULL,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0;

CREATE INDEX idx_wa_message_log_reply_to ON whatsapp_message_log (reply_to_log_id);

CREATE TABLE IF NOT EXISTS crm_quick_reply (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    owner_user_id INT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_crm_quick_reply_owner (owner_user_id),
    INDEX idx_crm_quick_reply_title (title)
);
