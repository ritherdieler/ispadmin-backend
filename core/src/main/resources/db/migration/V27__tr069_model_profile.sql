CREATE TABLE tr069_model_profile (
    product_class VARCHAR(64) NOT NULL PRIMARY KEY,
    manufacturer VARCHAR(128) NULL,
    wan_connection_device_index INT NOT NULL DEFAULT 1,
    wan_ip_connection_path VARCHAR(512) NOT NULL,
    wan_gpon_link_config_path VARCHAR(512) NULL,
    vlan_parameters_json TEXT NOT NULL,
    wlan24_path VARCHAR(512) NULL,
    wlan5_path VARCHAR(512) NULL,
    aliases_json TEXT NULL,
    source_device_id VARCHAR(128) NULL,
    source_serial VARCHAR(64) NULL,
    warnings_json TEXT NULL,
    imported_at DATETIME NOT NULL,
    imported_by VARCHAR(64) NULL,
    INDEX idx_tr069_model_profile_manufacturer (manufacturer)
);
