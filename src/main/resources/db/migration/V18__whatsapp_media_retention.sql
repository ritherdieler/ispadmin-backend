ALTER TABLE whatsapp_inbound_message
    ADD COLUMN media_purged_at DATETIME NULL;

ALTER TABLE whatsapp_message_log
    ADD COLUMN media_purged_at DATETIME NULL;
