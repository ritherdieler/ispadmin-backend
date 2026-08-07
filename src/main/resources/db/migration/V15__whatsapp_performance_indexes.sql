CREATE INDEX idx_wa_message_log_subscription_type_created
  ON whatsapp_message_log (subscription_id, message_type, created_at);

CREATE INDEX idx_wa_message_log_payment_type_created
  ON whatsapp_message_log (payment_id, message_type, created_at);

CREATE INDEX idx_wa_message_log_campaign_id
  ON whatsapp_message_log (campaign_id);

CREATE INDEX idx_wa_message_log_type_created
  ON whatsapp_message_log (message_type, created_at);

CREATE INDEX idx_wa_inbound_phone_read_at
  ON whatsapp_inbound_message (phone, read_at);

CREATE INDEX idx_wa_audit_action_created
  ON whatsapp_audit_log (action, created_at);
