-- One-shot: copy olt_mgr_* inventory from core staging schema to stg_oltgateway.
-- Do not run twice without review. Staging only with explicit confirmation.
-- Do not run in prod. olt_mgr_onu_optical_sample stays in the core schema.

CREATE DATABASE IF NOT EXISTS stg_oltgateway
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stg_oltgateway.olt_mgr_olt_model LIKE ispadmin_staging.olt_mgr_olt_model;
INSERT IGNORE INTO stg_oltgateway.olt_mgr_olt_model SELECT * FROM ispadmin_staging.olt_mgr_olt_model;

CREATE TABLE IF NOT EXISTS stg_oltgateway.olt_mgr_zone LIKE ispadmin_staging.olt_mgr_zone;
INSERT IGNORE INTO stg_oltgateway.olt_mgr_zone SELECT * FROM ispadmin_staging.olt_mgr_zone;

CREATE TABLE IF NOT EXISTS stg_oltgateway.olt_mgr_olt LIKE ispadmin_staging.olt_mgr_olt;
INSERT IGNORE INTO stg_oltgateway.olt_mgr_olt SELECT * FROM ispadmin_staging.olt_mgr_olt;

CREATE TABLE IF NOT EXISTS stg_oltgateway.olt_mgr_olt_pon_port LIKE ispadmin_staging.olt_mgr_olt_pon_port;
INSERT IGNORE INTO stg_oltgateway.olt_mgr_olt_pon_port SELECT * FROM ispadmin_staging.olt_mgr_olt_pon_port;

CREATE TABLE IF NOT EXISTS stg_oltgateway.olt_mgr_olt_vlan LIKE ispadmin_staging.olt_mgr_olt_vlan;
INSERT IGNORE INTO stg_oltgateway.olt_mgr_olt_vlan SELECT * FROM ispadmin_staging.olt_mgr_olt_vlan;

CREATE TABLE IF NOT EXISTS stg_oltgateway.olt_mgr_onu_type LIKE ispadmin_staging.olt_mgr_onu_type;
INSERT IGNORE INTO stg_oltgateway.olt_mgr_onu_type SELECT * FROM ispadmin_staging.olt_mgr_onu_type;

CREATE TABLE IF NOT EXISTS stg_oltgateway.olt_mgr_speed_profile LIKE ispadmin_staging.olt_mgr_speed_profile;
INSERT IGNORE INTO stg_oltgateway.olt_mgr_speed_profile SELECT * FROM ispadmin_staging.olt_mgr_speed_profile;

CREATE TABLE IF NOT EXISTS stg_oltgateway.olt_mgr_custom_template LIKE ispadmin_staging.olt_mgr_custom_template;
INSERT IGNORE INTO stg_oltgateway.olt_mgr_custom_template SELECT * FROM ispadmin_staging.olt_mgr_custom_template;

CREATE TABLE IF NOT EXISTS stg_oltgateway.olt_mgr_onu LIKE ispadmin_staging.olt_mgr_onu;
INSERT IGNORE INTO stg_oltgateway.olt_mgr_onu SELECT * FROM ispadmin_staging.olt_mgr_onu;

CREATE TABLE IF NOT EXISTS stg_oltgateway.olt_mgr_onu_status_current LIKE ispadmin_staging.olt_mgr_onu_status_current;
INSERT IGNORE INTO stg_oltgateway.olt_mgr_onu_status_current SELECT * FROM ispadmin_staging.olt_mgr_onu_status_current;

CREATE TABLE IF NOT EXISTS stg_oltgateway.olt_mgr_onu_service_port LIKE ispadmin_staging.olt_mgr_onu_service_port;
INSERT IGNORE INTO stg_oltgateway.olt_mgr_onu_service_port SELECT * FROM ispadmin_staging.olt_mgr_onu_service_port;

CREATE TABLE IF NOT EXISTS stg_oltgateway.olt_mgr_onu_extra_vlan LIKE ispadmin_staging.olt_mgr_onu_extra_vlan;
INSERT IGNORE INTO stg_oltgateway.olt_mgr_onu_extra_vlan SELECT * FROM ispadmin_staging.olt_mgr_onu_extra_vlan;

CREATE TABLE IF NOT EXISTS stg_oltgateway.olt_mgr_task LIKE ispadmin_staging.olt_mgr_task;
INSERT IGNORE INTO stg_oltgateway.olt_mgr_task SELECT * FROM ispadmin_staging.olt_mgr_task;

CREATE TABLE IF NOT EXISTS stg_oltgateway.olt_mgr_audit_log LIKE ispadmin_staging.olt_mgr_audit_log;
INSERT IGNORE INTO stg_oltgateway.olt_mgr_audit_log SELECT * FROM ispadmin_staging.olt_mgr_audit_log;

CREATE TABLE IF NOT EXISTS stg_oltgateway.olt_mgr_sync_run LIKE ispadmin_staging.olt_mgr_sync_run;
INSERT IGNORE INTO stg_oltgateway.olt_mgr_sync_run SELECT * FROM ispadmin_staging.olt_mgr_sync_run;
