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

El 2026-08-30 el WAR salió con defaults en false. **Después** se activó el piloto en `/opt/gigafiber/.env` (verificado 2026-08-31 10:15 America/Lima):

```
SERVICE_HEALTH_ENABLED=true
SERVICE_HEALTH_OPTICAL_ENABLED=true
SERVICE_HEALTH_ACS_ENABLED=true
SERVICE_HEALTH_CORRELATION_ENABLED=true
SERVICE_HEALTH_SHARED_INCIDENTS_ENABLED=true
SERVICE_HEALTH_ACTIONS_ENABLED=true
SERVICE_HEALTH_CONFIG_ENABLED=true
SERVICE_HEALTH_PILOT_SUBSCRIPTION_IDS=2310,2328
SERVICE_HEALTH_SHARED_INCIDENT_NOTIFICATIONS_ENABLED=false
```

HMAC de estaciones configurado (64 chars). Preset GenieACS `gigafiber-wifi-telemetry` **sí está** en NBI: evento `2 PERIODIC`, seriales `ZTEGDC47BF8F` y `ZTEGDC47DAD1` (F6600R).

V35 es aditivo (`CREATE TABLE IF NOT EXISTS`). Hibernate `ddl-auto=update` puede crear el esquema al arrancar.

## Notas

- El script de deploy exige árbol limpio: se commiteó el módulo antes de subir el WAR.
- Push de `develop` (backend y backoffice) pendiente.
- Hard refresh en el backoffice (assets con hash nuevo).
- As-built: backend `01-implementacion-analitica-consumo-ancho-banda.md` (Documento 1) y `03-implementacion-diagnostico-tecnico-convergente.md` (Documento 2). UI: `diagnostico-360-e-inteligencia-ancho-banda.md`.
