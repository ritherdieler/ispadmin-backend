-- Local only. olt_mgr_onu_autofind is not a Gateway table (not in RENAME scripts).
-- Drop leftover from core schema if present. Do not run on staging/prod without confirmation.

DROP TABLE IF EXISTS ispadmin_dev.olt_mgr_onu_autofind;
