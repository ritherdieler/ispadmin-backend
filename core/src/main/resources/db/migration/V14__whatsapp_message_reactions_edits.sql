ALTER TABLE whatsapp_message_log
    ADD COLUMN customer_reaction_emoji VARCHAR(16) NULL,
    ADD COLUMN edited_at DATETIME NULL,
    ADD COLUMN deleted_at DATETIME NULL,
    ADD COLUMN original_message VARCHAR(1000) NULL;

ALTER TABLE whatsapp_inbound_message
    ADD COLUMN agent_reaction_emoji VARCHAR(16) NULL;
