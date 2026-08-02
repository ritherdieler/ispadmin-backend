CREATE TABLE IF NOT EXISTS whatsapp_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(64) NOT NULL,
    operator_username VARCHAR(128) NULL,
    phone VARCHAR(32) NULL,
    resource VARCHAR(255) NULL,
    details VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_wa_audit_created (created_at),
    INDEX idx_wa_audit_action (action),
    INDEX idx_wa_audit_operator (operator_username)
);

DELETE t1
FROM whatsapp_message_log t1
INNER JOIN whatsapp_message_log t2
    ON t1.meta_message_id = t2.meta_message_id
   AND t1.meta_message_id IS NOT NULL
   AND t1.id < t2.id;

CREATE UNIQUE INDEX uk_whatsapp_message_log_meta_message_id
    ON whatsapp_message_log (meta_message_id);

CREATE INDEX idx_wa_message_log_phone_created
    ON whatsapp_message_log (phone, created_at);

CREATE INDEX idx_wa_message_log_created
    ON whatsapp_message_log (created_at);

CREATE INDEX idx_wa_inbound_phone_created
    ON whatsapp_inbound_message (phone, created_at);
