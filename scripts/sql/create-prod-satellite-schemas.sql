-- Option A for the unified WAR: empty satellite schemas in production.
-- Do not run until cutover. Creates schemas only; no DML, no DROP.
-- First boot then applies ACS Flyway V1–V5, OLT Gateway Flyway V1, Traffic ddl-auto=update.
-- Do not copy stg_* rows (lab). Do not RENAME olt_mgr_* out of ispadmin on first boot.

CREATE DATABASE IF NOT EXISTS prod_acs
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS prod_oltgateway
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS prod_traffic
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
