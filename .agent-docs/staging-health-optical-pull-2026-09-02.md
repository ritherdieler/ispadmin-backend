# Health: óptica/estado por pull HTTP (2026-09-02)

Health no escucha eventos Spring del Gateway. Dos modos (`service.health.optical-pull-mode`):

| Modo | Entorno | Qué hace |
|------|---------|----------|
| `samples` (default) | prod | `GET /optical-samples?oltId=&since=` sobre `status_current` |
| `live-sns` | staging overlay | `POST /onus/optical` con SNs del scope Health (`subscription_acs.lab` + tag `stg`) |

## API live (staging)

`POST /api/olt-gateway/onus/optical` body `{ "sns": ["VSOL0031C0B6"] }` (máx. 32).

Por SN: `display ont info by-sn` + óptica (SNMP si on; SSH `display ont optical-info {port} {ontId}` si no). Upsert `status_current` si hay fila `olt_mgr_onu`. SN ausente → `missing[]`, no 404.

## Health

`HealthOltPullService` (`service.health.olt-optical-pull-interval-ms`, default 90 s):

- `samples`: por OLT (`listOlts()`), cursor `optical-pull:{oltId}` en `service_health_cursor`.
- `live-sns`: `collectionSubscriptionIds()` → `fiberOnu.sn` → POST; ingesta la respuesta. Sin cursor.
- Filas → `HealthOltIngestPort.onOptical` + `onState` (`OltHistoryService`).
- HTTP genérico → `onOpticalFailure`. 404 loopback / `ResourceAccessException` → skip.

`POST /subscription/{id}/service-health/optical-refresh` llama el mismo POST con un SN (`RemoteActionService` → `pollAndIngest`).

## Gateway

- Eliminados `LabOpticalSshPollService` / scheduler / flags `lab-optical-ssh-*`.
- Gateway no importa `servicehealth.port.*`.
- Overlay `--with servicehealth,oltgateway` hornea `service.health.optical-pull-mode=live-sns`. Poll SNMP full-OLT sigue off en staging.

## E2E backoffice (2026-09-02)

Corrido contra staging **antes** de desplegar este WAR: `npm run e2e:service-health-staging` en el backoffice (`vite --mode staging --port 3010`) sobre `#2329`. Detalle: `ispadmin-backoffice/.agent-docs/e2e-service-health-staging-2026-09-02.md`.
