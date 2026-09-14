-- One-shot: copy TR-069 model profiles from Core schemas into ACS schemas.
-- Run ONCE on the VPS after ACS Flyway V1 (table exists) and BEFORE Core V50
-- (which drops ispadmin*.tr069_model_profile).
--
--   docker exec -i mysql8033 mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < scripts/sql/copy-tr069-profiles-to-acs.sql

INSERT INTO prod_acs.tr069_model_profile (
  product_class,
  manufacturer,
  wan_connection_device_index,
  wan_ip_connection_path,
  client_wan_ip_connection_path,
  wan_gpon_link_config_path,
  vlan_parameters_json,
  client_vlan_parameters_json,
  wlan24_path,
  wlan5_path,
  wifi_security_prep_json,
  aliases_json,
  source_device_id,
  source_serial,
  warnings_json,
  imported_at,
  imported_by
)
SELECT
  p.product_class,
  p.manufacturer,
  p.wan_connection_device_index,
  p.wan_ip_connection_path,
  p.client_wan_ip_connection_path,
  p.wan_gpon_link_config_path,
  p.vlan_parameters_json,
  p.client_vlan_parameters_json,
  p.wlan24_path,
  p.wlan5_path,
  COALESCE(p.wifi_security_prep_json, '[]'),
  p.aliases_json,
  p.source_device_id,
  p.source_serial,
  p.warnings_json,
  p.imported_at,
  p.imported_by
FROM ispadmin.tr069_model_profile p
WHERE NOT EXISTS (
  SELECT 1 FROM prod_acs.tr069_model_profile s WHERE s.product_class = p.product_class
);

UPDATE prod_acs.tr069_model_profile s
INNER JOIN ispadmin.tr069_model_profile p ON p.product_class = s.product_class
SET
  s.manufacturer = p.manufacturer,
  s.wan_connection_device_index = p.wan_connection_device_index,
  s.wan_ip_connection_path = p.wan_ip_connection_path,
  s.client_wan_ip_connection_path = p.client_wan_ip_connection_path,
  s.wan_gpon_link_config_path = p.wan_gpon_link_config_path,
  s.vlan_parameters_json = p.vlan_parameters_json,
  s.client_vlan_parameters_json = p.client_vlan_parameters_json,
  s.wlan24_path = p.wlan24_path,
  s.wlan5_path = p.wlan5_path,
  s.wifi_security_prep_json = COALESCE(p.wifi_security_prep_json, '[]'),
  s.aliases_json = p.aliases_json,
  s.source_device_id = p.source_device_id,
  s.source_serial = p.source_serial,
  s.warnings_json = p.warnings_json,
  s.imported_at = p.imported_at,
  s.imported_by = p.imported_by;

INSERT INTO stg_acs.tr069_model_profile (
  product_class,
  manufacturer,
  wan_connection_device_index,
  wan_ip_connection_path,
  client_wan_ip_connection_path,
  wan_gpon_link_config_path,
  vlan_parameters_json,
  client_vlan_parameters_json,
  wlan24_path,
  wlan5_path,
  wifi_security_prep_json,
  aliases_json,
  source_device_id,
  source_serial,
  warnings_json,
  imported_at,
  imported_by
)
SELECT
  p.product_class,
  p.manufacturer,
  p.wan_connection_device_index,
  p.wan_ip_connection_path,
  p.client_wan_ip_connection_path,
  p.wan_gpon_link_config_path,
  p.vlan_parameters_json,
  p.client_vlan_parameters_json,
  p.wlan24_path,
  p.wlan5_path,
  COALESCE(p.wifi_security_prep_json, '[]'),
  p.aliases_json,
  p.source_device_id,
  p.source_serial,
  p.warnings_json,
  p.imported_at,
  p.imported_by
FROM ispadmin.tr069_model_profile p
WHERE NOT EXISTS (
  SELECT 1 FROM stg_acs.tr069_model_profile s WHERE s.product_class = p.product_class
);

UPDATE stg_acs.tr069_model_profile s
INNER JOIN ispadmin.tr069_model_profile p ON p.product_class = s.product_class
SET
  s.manufacturer = p.manufacturer,
  s.wan_connection_device_index = p.wan_connection_device_index,
  s.wan_ip_connection_path = p.wan_ip_connection_path,
  s.client_wan_ip_connection_path = p.client_wan_ip_connection_path,
  s.wan_gpon_link_config_path = p.wan_gpon_link_config_path,
  s.vlan_parameters_json = p.vlan_parameters_json,
  s.client_vlan_parameters_json = p.client_vlan_parameters_json,
  s.wlan24_path = p.wlan24_path,
  s.wlan5_path = p.wlan5_path,
  s.wifi_security_prep_json = COALESCE(p.wifi_security_prep_json, '[]'),
  s.aliases_json = p.aliases_json,
  s.source_device_id = p.source_device_id,
  s.source_serial = p.source_serial,
  s.warnings_json = p.warnings_json,
  s.imported_at = p.imported_at,
  s.imported_by = p.imported_by;

SELECT COUNT(*) AS prod_acs_profiles FROM prod_acs.tr069_model_profile;
SELECT COUNT(*) AS stg_acs_profiles FROM stg_acs.tr069_model_profile;
SELECT product_class, aliases_json FROM stg_acs.tr069_model_profile ORDER BY product_class;
