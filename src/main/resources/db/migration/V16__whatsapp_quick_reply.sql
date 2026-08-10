CREATE TABLE IF NOT EXISTS quick_reply (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    shortcut VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_quick_reply_shortcut (shortcut),
    INDEX idx_quick_reply_title (title)
);
