-- One-shot: populate client_wan_ppp_connection_path in ACS staging.
-- Requires ACS Flyway V3 (column exists). Do NOT run from Core Flyway.
-- Prefer ACS Flyway V4 on ACS WAR deploy; this file is the manual fallback.
--
--   docker exec -i mysql8033 mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < scripts/sql/stg-acs-pppoe-wan-paths.sql

UPDATE stg_acs.tr069_model_profile
SET client_wan_ppp_connection_path = 'InternetGatewayDevice.WANDevice.1.WANConnectionDevice.2.WANPPPConnection.1'
WHERE product_class = 'V2804AX15T';

UPDATE stg_acs.tr069_model_profile
SET client_wan_ppp_connection_path = 'InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.2'
WHERE product_class = 'F6600R';
