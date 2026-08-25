ALTER TABLE tr069_model_profile
    ADD COLUMN client_wan_ip_connection_path VARCHAR(512) NULL AFTER wan_ip_connection_path,
    ADD COLUMN client_vlan_parameters_json TEXT NULL AFTER vlan_parameters_json;
