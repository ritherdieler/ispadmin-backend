# Gate de evaluación/recolección eliminado (2026-09-15)

Sergio: quitar el gate en **todos** los ambientes. **Sin deploy.**

## Qué se fue

- `ServiceHealthProperties.collects` / `pilotSubscriptionIds` / `pilotAcsDeviceIds`
- `HealthLabScopePort.collects` y `applyScope`
- `HealthSummary.pilotEnabled` / JSON `pilot_enabled`
- Banner 360 «Recolección desactivada» / «Evaluación activa»
- Assert e2e `pilot_enabled=true`
- Early-return `if (!scope.collects)` en Inform, óptica, OLT y diagnosis
- Env `SERVICE_HEALTH_PILOT_SUBSCRIPTION_IDS` y `SERVICE_HEALTH_PILOT_ACS_DEVICE_IDS`

## Qué queda

- Poll de tráfico, persistencia ACS Inform y óptica
- Tag GenieACS `lab` (candado de writes + Inform 60 s)
- Flags de módulo: `service.health.enabled`, `acs-enabled`, `optical-*`, `actions-enabled`, `correlation-enabled`
- `ServiceHealthScope.lab()` y `collectionSubscriptionIds()` = todos los ids del directorio
- Recolección lab **sigue on**

## Verificación

Suite unitaria `:core:test` de properties, scope, diagnosis, summary, Inform, óptica, OLT, remote action y wiring. No hay WAR / rsync / `deploy.sh` en este cambio.
