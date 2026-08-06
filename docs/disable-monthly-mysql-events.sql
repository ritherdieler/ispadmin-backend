-- Desactiva eventos MySQL de cierre mensual tras desplegar MonthlyBillingCloseScheduler (Spring).
-- Cadena Kotlin: último día del mes 23:50 America/Lima — snapshot suscripciones, recaudación, facturación masiva.
-- Ejecutar en ispadmin prod DESPUÉS del WAR con el scheduler unificado.

USE ispadmin;

ALTER EVENT GuardarResumenRecoleccionMensual DISABLE;
ALTER EVENT GuardarResumenSuscripciones DISABLE;
ALTER EVENT EjecutarRegistroPago DISABLE;

SELECT EVENT_NAME, STATUS, LAST_EXECUTED
FROM information_schema.EVENTS
WHERE EVENT_SCHEMA = 'ispadmin'
  AND EVENT_NAME IN (
    'GuardarResumenRecoleccionMensual',
    'GuardarResumenSuscripciones',
    'EjecutarRegistroPago'
  );
