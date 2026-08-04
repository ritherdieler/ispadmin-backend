CREATE TABLE IF NOT EXISTS whatsapp_marketing_optout (
    id INT AUTO_INCREMENT PRIMARY KEY,
    phone VARCHAR(20) NOT NULL,
    category VARCHAR(64) NULL,
    status VARCHAR(16) NOT NULL,
    updated_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_wa_marketing_optout_phone (phone)
);
