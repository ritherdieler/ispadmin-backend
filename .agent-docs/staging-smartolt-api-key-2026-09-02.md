# Staging SmartOLT API key alineada con prod (2026-09-02)

## Problema

El WAR staging resuelve `olt.service.api-key=${OLT_SERVICE_API_KEY:}`. En Tomcat faltaba `OLT_SERVICE_API_KEY` en `/opt/gigafiber/.env`, así que authorize SmartOLT devolvía 403 Invalid API key, `oltProvisionStatus` quedaba `PENDING` y el ping MK2 fallaba.

El WAR **prod** desplegado aún traía la key literal en `application-prod.properties` (deploy anterior a la migración a env).

## Fix

1. Copiar la key literal del WAR prod a `/opt/gigafiber/.env` como `OLT_SERVICE_API_KEY` (+ `OLT_SERVICE_BASE_URL`).
2. Recrear el servicio Compose `tomcat` (`docker compose up -d --force-recreate tomcat`). Un `docker restart` **no** recarga `env_file`.
3. Tras recreate, restaurar WARs desde host (`docker cp /opt/gigafiber/ispadmin{,-staging}.war tomcat9027:/usr/local/tomcat/webapps/`) — los wars no viven en volumen.

Staging y prod (próximo deploy del WAR prod) usan la **misma** key vía `.env`.

## Verificación

- `docker exec tomcat9027 printenv OLT_SERVICE_API_KEY` presente
- `GET …/onu/unconfigured_onus` con `X-Token` → HTTP 200
- Con `olt.provider.authorize=SMARTOLT`, `GET /onu/unconfigured_onus` debe devolver `olt_id` numérico de SmartOLT (p. ej. `"2"`), no el id del gateway (`gigafiber-ma5608t`). Si no, authorize responde `specify_olt_id`.

Catálogo: `vps-secrets-management.md` (SmartOLT). Criterio e2e: `staging-fiber-e2e-runbook.md`.
