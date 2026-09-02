-- F9: esquema propio de telemetria en el mismo servidor MySQL.
-- Los backups de negocio dejan de arrastrar net_diag_*, obs_* y *traffic*
-- cuando TELEMETRY_DATASOURCE_URL apunta a ispadmin_telemetry y las tablas
-- se mueven (operacion de corte, no de esta migracion).
-- Idempotente: CREATE DATABASE IF NOT EXISTS.

CREATE DATABASE IF NOT EXISTS `ispadmin_telemetry`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
