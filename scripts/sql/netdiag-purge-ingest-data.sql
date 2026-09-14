SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM net_diag_incident_event;
DELETE FROM net_diag_notification_log;
DELETE FROM net_diag_alert_decision;
DELETE FROM net_diag_incident;
DELETE FROM net_diag_trap_event;
DELETE FROM net_diag_olt_log_event;
DELETE FROM net_diag_probe_run;
DELETE FROM net_diag_audit_log;
DELETE FROM net_diag_maintenance_window;

SET FOREIGN_KEY_CHECKS = 1;

SELECT 'net_diag_incident' AS tbl, COUNT(*) AS cnt FROM net_diag_incident
UNION ALL SELECT 'net_diag_incident_event', COUNT(*) FROM net_diag_incident_event
UNION ALL SELECT 'net_diag_olt_log_event', COUNT(*) FROM net_diag_olt_log_event
UNION ALL SELECT 'net_diag_trap_event', COUNT(*) FROM net_diag_trap_event
UNION ALL SELECT 'net_diag_probe_run', COUNT(*) FROM net_diag_probe_run
UNION ALL SELECT 'net_diag_target', COUNT(*) FROM net_diag_target;
