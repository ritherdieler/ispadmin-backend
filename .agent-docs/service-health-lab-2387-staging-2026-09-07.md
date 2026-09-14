# Service-health lab scope — subscription 2387 (staging)

**Fecha:** 2026-09-07  
**Histórico:** primero se usó `SERVICE_HEALTH_LAB_SUBSCRIPTION_IDS=2387` en `.env` porque #2387 no tenía fila ACS / `lab=false`.

**Actual (mismo día):** membresía solo BD — ver `service-health-lab-membership-db-only-2026-09-07.md`. Backfill:

| subscription_id | genieacs_device_id | lab |
|-----------------|--------------------|-----|
| 2387 | `5872C9-F6600R-ZTEGDC47BFFD` | 1 |

## Verificación

- `GET /subscription/2387/service-health` → `identity.lab=true`
- `POST .../optical-refresh` → `CONFIRMED`
- Filas en `olt_mgr_onu_optical_sample` y `service_onu_state_event` para `subscription_id=2387`
- Lecturas: run_state `online`, Rx ≈ `-23.66` dBm

## Nota operativa

`docker compose up -d --force-recreate tomcat-staging` vacía `webapps`; restaurar con `docker cp /opt/gigafiber/ispadmin-staging*.war tomcat-staging:/usr/local/tomcat/webapps/`. Staging escucha en host `127.0.0.1:8081` (no `8080`).
