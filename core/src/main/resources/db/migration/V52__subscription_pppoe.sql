-- V52: access_mode + PPPoE / management columns. DDL only.
-- Leave every row at STATIC_IP. Do not backfill PPPOE_FIXED or usernames here.
-- UNIQUE is safe because pppoe_username stays NULL (MySQL allows many NULLs).
-- Resolve duplicate IPs before any out-of-band username backfill
-- (scripts/sql/backfill-subscription-pppoe-legacy.sql).

ALTER TABLE `subscription`
  ADD COLUMN `access_mode` VARCHAR(16) NOT NULL DEFAULT 'STATIC_IP',
  ADD COLUMN `pppoe_username` VARCHAR(64) NULL,
  ADD COLUMN `pppoe_password_enc` VARCHAR(512) NULL,
  ADD COLUMN `pppoe_profile` VARCHAR(64) NULL,
  ADD COLUMN `pppoe_provision_status` VARCHAR(24) NULL,
  ADD COLUMN `pppoe_last_ip` VARCHAR(45) NULL,
  ADD COLUMN `management_ip` VARCHAR(45) NULL,
  ADD COLUMN `management_mac` VARCHAR(32) NULL;

ALTER TABLE `subscription`
  ADD UNIQUE KEY `uk_subscription_pppoe_username` (`pppoe_username`);

CREATE INDEX `idx_subscription_access_mode` ON `subscription` (`access_mode`);
