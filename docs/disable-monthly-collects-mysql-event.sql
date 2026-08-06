-- Obsoleto: usar docs/disable-monthly-mysql-events.sql (cierre unificado Spring).
-- El reemplazo es MonthlyBillingCloseScheduler: último día del mes 23:50 America/Lima.

USE ispadmin;

ALTER EVENT GuardarResumenRecoleccionMensual DISABLE;

SELECT EVENT_NAME, STATUS, LAST_EXECUTED
FROM information_schema.EVENTS
WHERE EVENT_SCHEMA = 'ispadmin'
  AND EVENT_NAME = 'GuardarResumenRecoleccionMensual';
