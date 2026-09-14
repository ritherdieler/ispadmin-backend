-- One-shot: move Gateway olt_mgr_* from core schema to prod_oltgateway.
-- Health table olt_mgr_onu_optical_sample stays in ispadmin.
-- Do not run twice. Confirm explicitly before executing on prod.

CREATE DATABASE IF NOT EXISTS prod_oltgateway
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 0;

RENAME TABLE
  ispadmin.olt_mgr_olt_model TO prod_oltgateway.olt_mgr_olt_model,
  ispadmin.olt_mgr_zone TO prod_oltgateway.olt_mgr_zone,
  ispadmin.olt_mgr_onu_type TO prod_oltgateway.olt_mgr_onu_type,
  ispadmin.olt_mgr_custom_template TO prod_oltgateway.olt_mgr_custom_template,
  ispadmin.olt_mgr_speed_profile TO prod_oltgateway.olt_mgr_speed_profile,
  ispadmin.olt_mgr_olt TO prod_oltgateway.olt_mgr_olt,
  ispadmin.olt_mgr_onu TO prod_oltgateway.olt_mgr_onu,
  ispadmin.olt_mgr_onu_status_current TO prod_oltgateway.olt_mgr_onu_status_current,
  ispadmin.olt_mgr_task TO prod_oltgateway.olt_mgr_task,
  ispadmin.olt_mgr_audit_log TO prod_oltgateway.olt_mgr_audit_log,
  ispadmin.olt_mgr_onu_service_port TO prod_oltgateway.olt_mgr_onu_service_port,
  ispadmin.olt_mgr_onu_extra_vlan TO prod_oltgateway.olt_mgr_onu_extra_vlan,
  ispadmin.olt_mgr_olt_pon_port TO prod_oltgateway.olt_mgr_olt_pon_port,
  ispadmin.olt_mgr_olt_vlan TO prod_oltgateway.olt_mgr_olt_vlan,
  ispadmin.olt_mgr_sync_run TO prod_oltgateway.olt_mgr_sync_run;

SET FOREIGN_KEY_CHECKS = 1;
