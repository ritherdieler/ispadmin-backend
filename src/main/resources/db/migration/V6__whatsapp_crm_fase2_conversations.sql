CREATE TABLE IF NOT EXISTS crm_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel VARCHAR(32) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    subscription_id INT NULL,
    status VARCHAR(32) NOT NULL,
    assigned_agent_id INT NULL,
    priority INT NOT NULL DEFAULT 0,
    claimed_at DATETIME NULL,
    resolved_at DATETIME NULL,
    last_inbound_at DATETIME NULL,
    last_outbound_at DATETIME NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_crm_conversation_channel_phone (channel, phone),
    INDEX idx_crm_conversation_status (status),
    INDEX idx_crm_conversation_assigned (assigned_agent_id),
    INDEX idx_crm_conversation_last_inbound (last_inbound_at)
);

CREATE TABLE IF NOT EXISTS crm_assignment_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    from_user_id INT NULL,
    to_user_id INT NULL,
    note VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_crm_assignment_conversation (conversation_id),
    INDEX idx_crm_assignment_created (created_at),
    CONSTRAINT fk_crm_assignment_conversation
        FOREIGN KEY (conversation_id) REFERENCES crm_conversation (id)
);

CREATE TABLE IF NOT EXISTS crm_internal_note (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    author_id INT NOT NULL,
    text VARCHAR(2000) NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_crm_note_conversation (conversation_id),
    INDEX idx_crm_note_created (created_at),
    CONSTRAINT fk_crm_note_conversation
        FOREIGN KEY (conversation_id) REFERENCES crm_conversation (id)
);
