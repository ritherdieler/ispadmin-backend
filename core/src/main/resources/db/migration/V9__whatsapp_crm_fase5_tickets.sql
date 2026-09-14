CREATE TABLE IF NOT EXISTS ticket_conversation_link (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id INT NOT NULL,
    conversation_id BIGINT NOT NULL,
    created_by VARCHAR(128) NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_ticket_conversation_ticket (ticket_id),
    UNIQUE KEY uk_ticket_conversation_pair (ticket_id, conversation_id),
    INDEX idx_ticket_conversation_conversation (conversation_id),
    CONSTRAINT fk_ticket_conversation_conversation
        FOREIGN KEY (conversation_id) REFERENCES crm_conversation (id)
);
