-- One-shot: move Gateway olt_mgr_* from core schema to dev_oltgateway.
-- Health table olt_mgr_onu_optical_sample stays in ispadmin_dev.
-- Do not run twice. Local only.

CREATE DATABASE IF NOT EXISTS dev_oltgateway
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 0;

RENAME TABLE
  ispadmin_dev.olt_mgr_olt_model TO dev_oltgateway.olt_mgr_olt_model,
  ispadmin_dev.olt_mgr_zone TO dev_oltgateway.olt_mgr_zone,
  ispadmin_dev.olt_mgr_onu_type TO dev_oltgateway.olt_mgr_onu_type,
  ispadmin_dev.olt_mgr_custom_template TO dev_oltgateway.olt_mgr_custom_template,
  ispadmin_dev.olt_mgr_speed_profile TO dev_oltgateway.olt_mgr_speed_profile,
  ispadmin_dev.olt_mgr_olt TO dev_oltgateway.olt_mgr_olt,
  ispadmin_dev.olt_mgr_onu TO dev_oltgateway.olt_mgr_onu,
  ispadmin_dev.olt_mgr_onu_status_current TO dev_oltgateway.olt_mgr_onu_status_current,
  ispadmin_dev.olt_mgr_task TO dev_oltgateway.olt_mgr_task,
  ispadmin_dev.olt_mgr_audit_log TO dev_oltgateway.olt_mgr_audit_log,
  ispadmin_dev.olt_mgr_onu_service_port TO dev_oltgateway.olt_mgr_onu_service_port,
  ispadmin_dev.olt_mgr_onu_extra_vlan TO dev_oltgateway.olt_mgr_onu_extra_vlan,
  ispadmin_dev.olt_mgr_olt_pon_port TO dev_oltgateway.olt_mgr_olt_pon_port,
  ispadmin_dev.olt_mgr_olt_vlan TO dev_oltgateway.olt_mgr_olt_vlan,
  ispadmin_dev.olt_mgr_sync_run TO dev_oltgateway.olt_mgr_sync_run;

SET FOREIGN_KEY_CHECKS = 1;
