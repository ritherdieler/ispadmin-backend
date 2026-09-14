# Fixture NetDiag para pruebas de Diagnóstico 360 en staging

## Target estable

La tabla `net_diag_target` se provisiona en la base `ispadmin_staging` mediante:

```bash
mysql ispadmin_staging < scripts/sql/netdiag-target-staging-active-ccr.sql
```

El seed es idempotente y mantiene un único target llamado `STAGING-CCR-ACTIVE`. Selecciona el `network_device` de tipo `CLOUD_CORE_ROUTER` que no esté deshabilitado; prioriza el CCR2/MK2 (`38.224.231.4`, actualmente `network_device.id=8`). Si cambia el CCR activo, una nueva ejecución actualiza `device_ref_id` sin crear otro target.

El target usa polling NetDiag cada 60 segundos y la plantilla de interfaces críticas, Netwatch y versión RouterOS del CCR2. Las credenciales se leen del `network_device`; no se guardan en el seed.

## Suscripción de prueba

- Suscripción: `2329`
- Router esperado: `network_device.id=8` mientras CCR2 esté activo
- Cola de tráfico staging: `*727`
- Vista: `http://localhost:3010/subscriptions/2329/service-health`

## Validación después del seed

Esperar un ciclo de polling y comprobar que exista un `net_diag_probe_run` reciente con `status=SUCCESS` para el target. En el 360, `NETDIAG / collector`, CPU y señales del router deben dejar de aparecer como `Sin dato`.

Si el CCR activo cambia, ejecutar nuevamente el mismo seed y repetir la validación; no crear targets manuales adicionales para la prueba.
