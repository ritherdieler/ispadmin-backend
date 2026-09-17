-- Rehearsal only. Never target the Core prod or staging schema names.
-- Use with scripts/flyway-clone-ispadmin when a dump is available.

CREATE DATABASE IF NOT EXISTS scratch_acs
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS scratch_oltgateway
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS scratch_traffic
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
