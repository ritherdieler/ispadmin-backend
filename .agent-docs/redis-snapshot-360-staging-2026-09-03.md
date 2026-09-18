# Redis + snapshot 360 (staging) — 2026-09-03

GET `/subscription/{id}/service-health` deja de reevaluar siempre en vivo. Orden: cache Redis → fila `service_health_current` fresca → fallback `HealthEvidenceReader` + `DiagnosisEngine` (HTTP a traffic/gateway como antes).

## Artefactos

| Pieza | Dónde |
|-------|--------|
| Paquete compartido | `com.dscorp.wispadmin.events` (escaneado por core, traffic, oltgateway) |
| GET 360 | `HealthSummaryQueryService` |
| Consumer | `HealthSnapshotConsumer` (`snapshot-core`: óptica/tráfico) + `CpeInformEventConsumer` (`wifi-inform-core`: `cpe.inform`) |
| Productores | `SubscriptionTrafficPollService`, `TrafficAnomalyService`, `OltSignalPollService`, `LabOpticalSshPollService`, ACS/`CpeInformIngestService` (`cpe.inform`) |
| Local | `docker-compose.redis.yml`, `scripts/redis-local.sh` |
| Staging | `REDIS_HOST=redis` + `REDIS_PASSWORD` en `/opt/gigafiber/.env` (sin `REDIS_ENABLED`). Core staging default `enabled=true`. Prod sigue `false`. |

## Eventos del corte

`traffic.latest`, `traffic.poll-run`, `traffic.anomaly-opened`, `traffic.anomaly-cleared`, `onu.optical`, `onu.state`.

Stream `gigafiber.events`, `MAXLEN ~ 20000`, grupos `snapshot-core`, `wifi-inform-core` y `cpe-provision-core`. Cache TTL = `service.health.snapshot-fresh-seconds` (default 60). Live hash `health:live:{id}:traffic|onu`. Con Redis on, los beans no-op no se registran. Carril Inform: [wifi-inform-consumer-lane-2026-09-18.md](./wifi-inform-consumer-lane-2026-09-18.md).

## Fallback

Si Redis está caído o `REDIS_ENABLED=false`, el bus es no-op y el GET 360 usa el lector HTTP. El core no debe fallar el arranque.

## Deploy staging

1. Servicio `redis` en `/opt/gigafiber/docker-compose.yml` (`maxmemory 128mb`, AOF, `requirepass`, sin puerto público).
2. Upsert `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` en `.env` (sin `REDIS_ENABLED`, para no encender prod). Recrear Tomcat **y** restaurar `ispadmin.war` de prod (`deploy.sh --war-only --env staging` copia staging + restaura prod).
3. `./scripts/deploy.sh --env staging --with servicehealth,oltgateway,netdiag,traffic`

Verificado 2026-09-03 15:06 Lima: Redis `PONG`, stream `gigafiber.events`, health prod+staging+traffic+oltgateway 200, GET 360 `#2360` ACS `FRESH`. Prod sigue `1.0.3+83de8fe` / Redis off.

E2E (sin nueva alta ni cleanup; #2360 viva): búsqueda Android DNI `98415203` → `E2E_SEARCH_STAGING_OK`; Playwright 360 → RX −19.46 dBm, ACS FRESH.

Prod hornea `gigafiber.redis.enabled=false` (ignora `REDIS_ENABLED` del `.env` compartido). Traffic y gateway staging hornean `enabled=true` y `host=redis` en el WAR; la password sigue en `REDIS_PASSWORD`.

Nombres de secretos: [vps-secrets-management.md](./vps-secrets-management.md). Transporte: [subsistemas-desacople-transporte.md](./subsistemas-desacople-transporte.md).
