-- One-shot: copy traffic tables from core schema to prod_traffic and label samples by IP.
-- Do not run twice without review. Production only with explicit confirmation.

CREATE DATABASE IF NOT EXISTS prod_traffic
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS prod_traffic.traffic_router (
  id INT NOT NULL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  host VARCHAR(64) NOT NULL,
  username VARCHAR(80) NOT NULL,
  password VARCHAR(120) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1
);

INSERT INTO prod_traffic.traffic_router (id, name, host, username, password, enabled)
SELECT nd.id,
       COALESCE(nd.name, CONCAT('router-', nd.id)),
       nd.ip_address,
       COALESCE(nd.username, ''),
       COALESCE(nd.password, ''),
       IF(nd.disabled = 1, 0, 1)
FROM ispadmin.network_device nd
WHERE nd.network_device_type IN ('FIBER_ROUTER', 'CLOUD_CORE_ROUTER', 'WIRELESS_ROUTER')
  AND nd.ip_address IS NOT NULL AND TRIM(nd.ip_address) <> ''
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  host = VALUES(host),
  username = VALUES(username),
  password = VALUES(password),
  enabled = VALUES(enabled);

CREATE TABLE IF NOT EXISTS prod_traffic.subscription_traffic_sample LIKE ispadmin.subscription_traffic_sample;
INSERT INTO prod_traffic.subscription_traffic_sample
SELECT * FROM ispadmin.subscription_traffic_sample;

ALTER TABLE prod_traffic.subscription_traffic_sample
  MODIFY COLUMN subscription_id INT NULL;

UPDATE prod_traffic.subscription_traffic_sample s
INNER JOIN ispadmin.subscription sub ON s.subscription_id = sub.id
SET s.client_ip = sub.ip
WHERE (s.client_ip IS NULL OR TRIM(s.client_ip) = '')
  AND sub.ip IS NOT NULL AND TRIM(sub.ip) <> '';

UPDATE prod_traffic.subscription_traffic_sample
SET client_ip = CONCAT('unknown-', id)
WHERE client_ip IS NULL OR TRIM(client_ip) = '';

DELETE s1 FROM prod_traffic.subscription_traffic_sample s1
INNER JOIN prod_traffic.subscription_traffic_sample s2
  ON s1.client_ip = s2.client_ip
 AND s1.bucket_start = s2.bucket_start
 AND s1.id < s2.id;

ALTER TABLE prod_traffic.subscription_traffic_sample
  DROP INDEX uk_traffic_sample_sub_bucket;

ALTER TABLE prod_traffic.subscription_traffic_sample
  ADD UNIQUE INDEX uk_traffic_sample_ip_bucket (client_ip, bucket_start);

CREATE TABLE IF NOT EXISTS prod_traffic.traffic_counter_state LIKE ispadmin.traffic_counter_state;
INSERT IGNORE INTO prod_traffic.traffic_counter_state
SELECT * FROM ispadmin.traffic_counter_state;

CREATE TABLE IF NOT EXISTS prod_traffic.subscription_traffic_hourly LIKE ispadmin.subscription_traffic_hourly;
INSERT INTO prod_traffic.subscription_traffic_hourly
SELECT * FROM ispadmin.subscription_traffic_hourly;

CREATE TABLE IF NOT EXISTS prod_traffic.subscription_traffic_daily LIKE ispadmin.subscription_traffic_daily;
INSERT INTO prod_traffic.subscription_traffic_daily
SELECT * FROM ispadmin.subscription_traffic_daily;

CREATE TABLE IF NOT EXISTS prod_traffic.subscription_traffic_monthly LIKE ispadmin.subscription_traffic_monthly;
INSERT INTO prod_traffic.subscription_traffic_monthly
SELECT * FROM ispadmin.subscription_traffic_monthly;

CREATE TABLE IF NOT EXISTS prod_traffic.subscription_traffic_five_minute LIKE ispadmin.subscription_traffic_five_minute;
INSERT INTO prod_traffic.subscription_traffic_five_minute
SELECT * FROM ispadmin.subscription_traffic_five_minute;

CREATE TABLE IF NOT EXISTS prod_traffic.traffic_source_run LIKE ispadmin.traffic_source_run;
INSERT INTO prod_traffic.traffic_source_run
SELECT * FROM ispadmin.traffic_source_run;

CREATE TABLE IF NOT EXISTS prod_traffic.traffic_anomaly_event LIKE ispadmin.traffic_anomaly_event;
INSERT INTO prod_traffic.traffic_anomaly_event
SELECT * FROM ispadmin.traffic_anomaly_event;

CREATE TABLE IF NOT EXISTS prod_traffic.traffic_aggregation_watermark LIKE ispadmin.traffic_aggregation_watermark;
INSERT IGNORE INTO prod_traffic.traffic_aggregation_watermark
SELECT * FROM ispadmin.traffic_aggregation_watermark;

CREATE TABLE IF NOT EXISTS prod_traffic.traffic_aggregation_run LIKE ispadmin.traffic_aggregation_run;
INSERT INTO prod_traffic.traffic_aggregation_run
SELECT * FROM ispadmin.traffic_aggregation_run;

CREATE TABLE IF NOT EXISTS prod_traffic.network_traffic_hour_of_day LIKE ispadmin.network_traffic_hour_of_day;
INSERT IGNORE INTO prod_traffic.network_traffic_hour_of_day
SELECT * FROM ispadmin.network_traffic_hour_of_day;
