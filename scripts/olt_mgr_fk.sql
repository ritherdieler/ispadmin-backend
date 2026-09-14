-- Foreign keys for olt_mgr_* (apply manually after data cleanup).
-- Hibernate ddl-auto=update does not reliably create these FKs.

-- Model catalog
ALTER TABLE olt_mgr_olt
  ADD CONSTRAINT fk_olt_model
  FOREIGN KEY (model_id) REFERENCES olt_mgr_olt_model (id);

-- Critical (non-null parents already populated by sync/authorize)
ALTER TABLE olt_mgr_onu
  ADD CONSTRAINT fk_onu_olt
  FOREIGN KEY (olt_id) REFERENCES olt_mgr_olt (id);

ALTER TABLE olt_mgr_onu_status_current
  ADD CONSTRAINT fk_status_onu
  FOREIGN KEY (onu_id) REFERENCES olt_mgr_onu (id);

-- Nullable catalog / task links
ALTER TABLE olt_mgr_onu
  ADD CONSTRAINT fk_onu_zone
  FOREIGN KEY (zone_id) REFERENCES olt_mgr_zone (id);

ALTER TABLE olt_mgr_onu
  ADD CONSTRAINT fk_onu_type
  FOREIGN KEY (onu_type_id) REFERENCES olt_mgr_onu_type (id);

ALTER TABLE olt_mgr_onu
  ADD CONSTRAINT fk_onu_custom_template
  FOREIGN KEY (custom_template_id) REFERENCES olt_mgr_custom_template (id);

ALTER TABLE olt_mgr_task
  ADD CONSTRAINT fk_task_olt
  FOREIGN KEY (olt_id) REFERENCES olt_mgr_olt (id);

ALTER TABLE olt_mgr_task
  ADD CONSTRAINT fk_task_onu
  FOREIGN KEY (onu_id) REFERENCES olt_mgr_onu (id);

ALTER TABLE olt_mgr_audit_log
  ADD CONSTRAINT fk_audit_olt
  FOREIGN KEY (olt_id) REFERENCES olt_mgr_olt (id);

ALTER TABLE olt_mgr_audit_log
  ADD CONSTRAINT fk_audit_onu
  FOREIGN KEY (onu_id) REFERENCES olt_mgr_onu (id);

-- Stub tables
ALTER TABLE olt_mgr_onu_service_port
  ADD CONSTRAINT fk_service_port_onu
  FOREIGN KEY (onu_id) REFERENCES olt_mgr_onu (id);

ALTER TABLE olt_mgr_onu_service_port
  ADD CONSTRAINT fk_service_port_download_speed
  FOREIGN KEY (download_speed_profile_id) REFERENCES olt_mgr_speed_profile (id);

ALTER TABLE olt_mgr_onu_service_port
  ADD CONSTRAINT fk_service_port_upload_speed
  FOREIGN KEY (upload_speed_profile_id) REFERENCES olt_mgr_speed_profile (id);

ALTER TABLE olt_mgr_onu_extra_vlan
  ADD CONSTRAINT fk_extra_vlan_onu
  FOREIGN KEY (onu_id) REFERENCES olt_mgr_onu (id);

ALTER TABLE olt_mgr_olt_pon_port
  ADD CONSTRAINT fk_pon_port_olt
  FOREIGN KEY (olt_id) REFERENCES olt_mgr_olt (id);

ALTER TABLE olt_mgr_olt_vlan
  ADD CONSTRAINT fk_olt_vlan_olt
  FOREIGN KEY (olt_id) REFERENCES olt_mgr_olt (id);
