# Deploy prod — diagnóstico convergente (opt-in, desactivado) — 2026-08-30

## Backend

| Campo | Valor |
|-------|--------|
| Commit | `84faf53` — `feat(service-health): diagnóstico convergente opt-in (piloto desactivado)` |
| Release | `1.0.3+84faf53` |
| Deploy | `./scripts/deploy.sh --deploy` OK; deploy event registrado |
| Smoke | `GET https://api.gigafiberperu.cloud/ispadmin/` → HTTP 200 |

## Backoffice

| Campo | Valor |
|-------|--------|
| Commit | `924402d` — `feat(service-health): vista 360 y acceso técnico CPE/ONU/NOC` |
| Build | `npm run build -- --mode production` OK |
| Destino | `rsync -avz --delete dist/` → `/var/www/gigafiber/backoffice/` |
| Smoke | `https://backoffice.gigafiberperu.cloud/` → HTTP 200 |
| Asset | `assets/ServiceHealthPage-DeLHfwNc.js` → HTTP 200 |

## Activación

Todas las banderas quedan en **false** (default del WAR). No se enviaron tareas ACS ni acciones remotas. El piloto no está habilitado en `/opt/gigafiber/.env`.

```
SERVICE_HEALTH_ENABLED=false
SERVICE_HEALTH_OPTICAL_ENABLED=false
SERVICE_HEALTH_ACS_ENABLED=false
SERVICE_HEALTH_CORRELATION_ENABLED=false
SERVICE_HEALTH_SHARED_INCIDENTS_ENABLED=false
SERVICE_HEALTH_ACTIONS_ENABLED=false
SERVICE_HEALTH_CONFIG_ENABLED=false
```

V35 es aditivo (`CREATE TABLE IF NOT EXISTS`). Hibernate `ddl-auto=update` puede crear el esquema al arrancar. No se aplicó el preset GenieACS `gigafiber-wifi-telemetry`.

## Notas

- El script de deploy exige árbol limpio: se commiteó el módulo antes de subir el WAR.
- Push de `develop` (backend y backoffice) pendiente.
- Hard refresh en el backoffice (assets con hash nuevo).
