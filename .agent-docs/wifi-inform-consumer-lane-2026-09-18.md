# Carril `wifi-inform-core` — 2026-09-18

Desbloquea series Wi‑Fi cuando `snapshot-core` está ocupado con óptica/tráfico. Canónico: [wifi-on-inform-flujo-acs-gateway-core.md](./wifi-on-inform-flujo-acs-gateway-core.md).

## Cambio

Mismo stream `gigafiber.events` (namespaced). Tres grupos, un hilo cada uno:

| Grupo | Hilo | Atiende |
|-------|------|---------|
| `wifi-inform-core` | `gigafiber-redis-wifi-inform-core` | `cpe.inform` → persist `acs_wifi_*` **sin** `reevaluate` |
| `snapshot-core` | `gigafiber-redis-snapshot-core` | óptica / estado / tráfico → persist + hash live; **sin** `reevaluate` |
| `cpe-provision-core` | sin cambio | `cpe.provisioning` |

`HealthSnapshotIngestService` ignora `cpe.inform` (ACK y sale). El diagnóstico 360 del GET recalcula si la foto tiene más de 60 s. `WifiCharts` lee las tablas de series, no el snapshot.

Propiedad: `gigafiber.redis.inform-consumer-group=wifi-inform-core`.

Código: `CpeInformEventConsumer`, `RedisStreamConsumerRuntime.groups()`.

## Por qué

Un solo hilo reevaluaba cada `traffic.latest` y cada ONU de `onu.optical-batch` (400–2300 ms). Lag = MAXLEN ≈ 20k; los Informs se recortaban antes del persist. ACS last-state fresco, series Core viejas.

## Ops post-deploy

El grupo nuevo se crea en `0-0` y barre la ventana; ACK de no-Inform es barato. Si `snapshot-core` sigue clavado, `XGROUP SETID … snapshot-core $` (óptica se rellena en el próximo poll SNMP ~10 min).

Verificar: thread `gigafiber-redis-wifi-inform-core`, lag del grupo bajo, `acs_wifi_count_sample.inform_at` avanzando.

## Tests

`CpeInformEventConsumerTest`, `CpeInformIngestWiringTest`, `HealthSnapshotIngestServiceTest`, `SchedulingConfigurationTest`.
