CREATE TABLE IF NOT EXISTS crm_event_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_crm_event_created (created_at)
);
