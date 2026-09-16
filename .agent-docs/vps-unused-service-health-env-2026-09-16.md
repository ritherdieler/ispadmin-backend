# VPS — quitar env 360 sin binding — 2026-09-16

`/opt/gigafiber/.env` compartido por `tomcat9027` y `tomcat-staging`. Backup: `.env.bak.20260916160925`.

## Quitadas (sin binding en el WAR)

- `SERVICE_HEALTH_PILOT_SUBSCRIPTION_IDS`
- `SERVICE_HEALTH_PILOT_ACS_DEVICE_IDS`
- `SERVICE_HEALTH_SHARED_INCIDENT_NOTIFICATIONS_ENABLED`
- `SERVICE_HEALTH_ACS_POLL_ENABLED`

No estaban: `SERVICE_HEALTH_OPTICAL_PULL_ENABLED`, `SERVICE_HEALTH_LAB_SUBSCRIPTION_IDS`.

## Se dejaron (sí enlazan `service.health.*`)

`ENABLED`, `OPTICAL_ENABLED`, `ACS_ENABLED`, `CORRELATION_ENABLED`, `SHARED_INCIDENTS_ENABLED`, `ACTIONS_ENABLED`, `CONFIG_ENABLED`, `STATION_HMAC_KEY`.

No se recreó Tomcat: esas keys ya no las lee el código. El `env_file` del contenedor se refresca en el próximo `up -d` / deploy.
