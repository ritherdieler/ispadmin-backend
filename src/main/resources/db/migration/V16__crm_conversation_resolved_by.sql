ALTER TABLE crm_conversation
    ADD COLUMN resolved_by_agent_id INT NULL AFTER resolved_at;

CREATE INDEX idx_crm_conversation_resolved_by ON crm_conversation (resolved_by_agent_id);
