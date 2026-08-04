ALTER TABLE whatsapp_message_log
    ADD COLUMN callback_id VARCHAR(64) NULL,
    ADD INDEX idx_wa_message_log_callback_id (callback_id);
