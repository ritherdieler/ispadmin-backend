CREATE TABLE subscription_acs (
    subscription_id INT NOT NULL,
    genieacs_device_id VARCHAR(128) NULL,
    serial_suffix VARCHAR(6) NULL,
    smartolt_serial VARCHAR(64) NULL,
    provision_status VARCHAR(32) NULL,
    last_error VARCHAR(500) NULL,
    provisioned_at DATETIME NULL,
    updated_at DATETIME NOT NULL,
    last_inform_at DATETIME NULL,
    product_class VARCHAR(64) NULL,
    oui VARCHAR(16) NULL,
    manufacturer VARCHAR(128) NULL,
    connection_request_url VARCHAR(512) NULL,
    wan_ip_cache VARCHAR(45) NULL,
    ssid_24 VARCHAR(32) NULL,
    ssid_5 VARCHAR(32) NULL,
    software_version VARCHAR(64) NULL,
    hardware_version VARCHAR(64) NULL,
    last_boot_at DATETIME NULL,
    last_task_id VARCHAR(64) NULL,
    last_task_status VARCHAR(32) NULL,
    last_task_at DATETIME NULL,
    PRIMARY KEY (subscription_id),
    CONSTRAINT fk_subscription_acs_subscription
        FOREIGN KEY (subscription_id) REFERENCES subscription (id)
);

CREATE INDEX idx_subscription_acs_device_id
    ON subscription_acs (genieacs_device_id);

CREATE INDEX idx_subscription_acs_last_inform
    ON subscription_acs (last_inform_at);
