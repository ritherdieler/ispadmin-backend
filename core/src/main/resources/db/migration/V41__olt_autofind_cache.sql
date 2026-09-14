-- F5: cache propia del autofind de la OLT.
-- Permite servir GET /onu/unconfigured_onus sin llamar a SmartOLT en vivo.
-- Idempotente: hasta F9 esta tabla la crea tambien ddl-auto=update.

CREATE TABLE IF NOT EXISTS olt_mgr_onu_autofind (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sn VARCHAR(32) NOT NULL,
    frame INT NOT NULL DEFAULT 0,
    board INT NOT NULL DEFAULT 0,
    port INT NOT NULL DEFAULT 0,
    pon_type VARCHAR(8) NOT NULL DEFAULT 'gpon',
    vendor_id VARCHAR(32) NULL,
    equipment_id VARCHAR(64) NULL,
    software_version VARCHAR(64) NULL,
    autofind_time VARCHAR(64) NULL,
    source VARCHAR(16) NOT NULL DEFAULT 'background',
    first_seen_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_omoa_sn (sn),
    KEY idx_omoa_last_seen (last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
