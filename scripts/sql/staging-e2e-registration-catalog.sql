-- Catálogo mínimo en ispadmin_staging para e2e registro FIBER Android.
-- Copia idempotente desde ispadmin (prod): place, mufa, nap_box, plan,
-- network_device, ip_pool. Perfiles TR-069 viven en ACS: prod_acs → stg_acs
-- (F6600R alias F6600RV9.0.21). Requiere ACS Flyway V1 y copy-tr069-profiles-to-acs.sql.
--
-- Aplicar en el VPS:
--   mysql < scripts/sql/staging-e2e-registration-catalog.sql
--
-- Fixture ubicación e2e FIBER (obligatorio):
--   lat=-11.2156 lon=-77.4107 = coords nap_box NO-001
--   findByLocation → 200 "9 de octubre"; napbox/near incluye NO-001
-- NO usar place.lat/lon (-11.2177/-77.4137): dentro del polígono pero near ≠ otras NAP.
-- NO intercambiar lat/lon. WKT: POINT(longitude latitude).

SET FOREIGN_KEY_CHECKS = 0;

INSERT INTO ispadmin_staging.place (id, name, latitude, longitude, area)
SELECT
  p.id,
  p.name,
  p.latitude,
  p.longitude,
  CASE
    WHEN p.area IS NULL THEN NULL
    ELSE ST_GeomFromText(ST_AsText(p.area), 4326)
  END
FROM ispadmin.place p
WHERE NOT EXISTS (
  SELECT 1 FROM ispadmin_staging.place s WHERE s.id = p.id
);

UPDATE ispadmin_staging.place s
INNER JOIN ispadmin.place p ON p.id = s.id
SET
  s.latitude = p.latitude,
  s.longitude = p.longitude,
  s.name = p.name,
  s.area = CASE
    WHEN p.area IS NULL THEN NULL
    ELSE ST_GeomFromText(ST_AsText(p.area), 4326)
  END;

INSERT INTO ispadmin_staging.mufa (id, latitude, longitude, reference, threads)
SELECT m.id, m.latitude, m.longitude, m.reference, m.threads
FROM ispadmin.mufa m
WHERE NOT EXISTS (
  SELECT 1 FROM ispadmin_staging.mufa s WHERE s.id = m.id
);

INSERT INTO ispadmin_staging.nap_box (
  id, address, code, latitude, longitude, ports_number, mufa_id, olt_board, olt_id, olt_port, place_id
)
SELECT
  n.id, n.address, n.code, n.latitude, n.longitude, n.ports_number, n.mufa_id, n.olt_board, n.olt_id, n.olt_port, n.place_id
FROM ispadmin.nap_box n
WHERE NOT EXISTS (
  SELECT 1 FROM ispadmin_staging.nap_box s WHERE s.id = n.id
);

UPDATE ispadmin_staging.nap_box s
INNER JOIN ispadmin.nap_box n ON n.id = s.id
SET
  s.address = n.address,
  s.code = n.code,
  s.latitude = n.latitude,
  s.longitude = n.longitude,
  s.ports_number = n.ports_number,
  s.mufa_id = n.mufa_id,
  s.olt_board = n.olt_board,
  s.olt_id = n.olt_id,
  s.olt_port = n.olt_port,
  s.place_id = n.place_id;

INSERT INTO ispadmin_staging.plan (
  id, download_speed, name, price, type, upload_speed, is_active
)
SELECT
  p.id, p.download_speed, p.name, p.price, p.type, p.upload_speed, p.is_active
FROM ispadmin.plan p
WHERE NOT EXISTS (
  SELECT 1 FROM ispadmin_staging.plan s WHERE s.id = p.id
);

UPDATE ispadmin_staging.plan s
INNER JOIN ispadmin.plan p ON p.id = s.id
SET
  s.name = p.name,
  s.price = p.price,
  s.download_speed = p.download_speed,
  s.upload_speed = p.upload_speed,
  s.type = p.type,
  s.is_active = p.is_active;

INSERT INTO ispadmin_staging.network_device (
  id, ip_address, name, network_device_type, password, username, disabled, vlan_id
)
SELECT
  nd.id, nd.ip_address, nd.name, nd.network_device_type, nd.password, nd.username, nd.disabled, nd.vlan_id
FROM ispadmin.network_device nd
WHERE nd.id = 8
  AND NOT EXISTS (
    SELECT 1 FROM ispadmin_staging.network_device d WHERE d.id = 8
  );

UPDATE ispadmin_staging.network_device s
INNER JOIN ispadmin.network_device nd ON nd.id = s.id
SET
  s.name = nd.name,
  s.password = nd.password,
  s.username = nd.username,
  s.ip_address = nd.ip_address,
  s.network_device_type = nd.network_device_type,
  s.vlan_id = nd.vlan_id,
  s.disabled = nd.disabled
WHERE s.id = 8;

INSERT INTO ispadmin_staging.user (
  id, born_date, device_token, dni, email, last_name, name, password, phone, type, username, verified
)
SELECT
  p.id, p.born_date, p.device_token, p.dni, p.email, p.last_name, p.name, p.password, p.phone, p.type, p.username, p.verified
FROM ispadmin.user p
WHERE NOT EXISTS (
  SELECT 1 FROM ispadmin_staging.user s WHERE s.id = p.id
);

UPDATE ispadmin_staging.user s
INNER JOIN ispadmin.user p ON p.id = s.id
SET
  s.born_date = p.born_date,
  s.device_token = p.device_token,
  s.dni = p.dni,
  s.email = p.email,
  s.last_name = p.last_name,
  s.name = p.name,
  s.password = p.password,
  s.phone = p.phone,
  s.type = p.type,
  s.verified = p.verified;

DELETE FROM ispadmin_staging.ip_pool
WHERE ip_segment <> '192.168.250.1/24';

INSERT INTO ispadmin_staging.ip_pool (id, created_at, ip_segment, host_device_id, is_eligible)
SELECT 250, UNIX_TIMESTAMP() * 1000, '192.168.250.1/24', 8, 1
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM ispadmin_staging.ip_pool p WHERE p.ip_segment = '192.168.250.1/24'
);

INSERT INTO stg_acs.tr069_model_profile (
  product_class,
  aliases_json,
  imported_at,
  imported_by,
  manufacturer,
  source_device_id,
  source_serial,
  vlan_parameters_json,
  wan_connection_device_index,
  wan_gpon_link_config_path,
  wan_ip_connection_path,
  warnings_json,
  wlan24_path,
  wlan5_path,
  wifi_security_prep_json,
  client_vlan_parameters_json,
  client_wan_ip_connection_path
)
SELECT
  p.product_class,
  p.aliases_json,
  p.imported_at,
  p.imported_by,
  p.manufacturer,
  p.source_device_id,
  p.source_serial,
  p.vlan_parameters_json,
  p.wan_connection_device_index,
  p.wan_gpon_link_config_path,
  p.wan_ip_connection_path,
  p.warnings_json,
  p.wlan24_path,
  p.wlan5_path,
  p.wifi_security_prep_json,
  p.client_vlan_parameters_json,
  p.client_wan_ip_connection_path
FROM prod_acs.tr069_model_profile p
WHERE NOT EXISTS (
  SELECT 1 FROM stg_acs.tr069_model_profile s WHERE s.product_class = p.product_class
);

UPDATE stg_acs.tr069_model_profile s
INNER JOIN prod_acs.tr069_model_profile p ON p.product_class = s.product_class
SET
  s.aliases_json = p.aliases_json,
  s.imported_at = p.imported_at,
  s.imported_by = p.imported_by,
  s.manufacturer = p.manufacturer,
  s.source_device_id = p.source_device_id,
  s.source_serial = p.source_serial,
  s.vlan_parameters_json = p.vlan_parameters_json,
  s.wan_connection_device_index = p.wan_connection_device_index,
  s.wan_gpon_link_config_path = p.wan_gpon_link_config_path,
  s.wan_ip_connection_path = p.wan_ip_connection_path,
  s.warnings_json = p.warnings_json,
  s.wlan24_path = p.wlan24_path,
  s.wlan5_path = p.wlan5_path,
  s.wifi_security_prep_json = p.wifi_security_prep_json,
  s.client_vlan_parameters_json = p.client_vlan_parameters_json,
  s.client_wan_ip_connection_path = p.client_wan_ip_connection_path;

SET FOREIGN_KEY_CHECKS = 1;

SELECT COUNT(*) AS staging_places FROM ispadmin_staging.place;
SELECT COUNT(*) AS staging_places_with_area FROM ispadmin_staging.place WHERE area IS NOT NULL;
SELECT COUNT(*) AS staging_nap_boxes FROM ispadmin_staging.nap_box;
SELECT COUNT(*) AS staging_fiber_plans FROM ispadmin_staging.plan WHERE type = 'FIBER' AND is_active = 1;
SELECT id, name, network_device_type, disabled FROM ispadmin_staging.network_device WHERE id = 8;
SELECT ip_segment, host_device_id FROM ispadmin_staging.ip_pool WHERE ip_segment = '192.168.250.1/24';
SELECT product_class, aliases_json FROM stg_acs.tr069_model_profile ORDER BY product_class;

SELECT p.id, p.name
FROM ispadmin_staging.place p
WHERE p.area IS NOT NULL
  AND ST_Contains(
    ST_SRID(p.area, 4326),
    ST_GeomFromText('POINT(-77.4107 -11.2156)', 4326)
  )
LIMIT 1;
