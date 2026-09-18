# Core series gap — `snapshot-core` no come `cpe.inform` (2026-09-17)

Evidencia operativa. Canónico: [wifi-on-inform-flujo-acs-gateway-core.md](./wifi-on-inform-flujo-acs-gateway-core.md).

## Síntoma

Tras el apply del `ext` `wifi-inform-notify` (prod → `tomcat9027`, lab también staging), ACS last-state (`prod_acs.cpe_record`) queda fresco. Las series Core no.

| Check | Valor (2026-09-18 ~02:12Z / 21:12 Lima) |
|-------|----------------------------------------|
| ACS `MAX(wifi_observed_at)` | `2026-09-17 21:12:40` (Lima; al minuto) |
| Core `acs_wifi_count_sample` `MAX(inform_at)` | `2026-09-17 06:23:18` |
| Filas Core `inform_at >= 2026-09-17 20:00` | **0** |
| Stations `MAX(observed_at)` | `2026-09-16 14:36:40` |
| Logs 6 h `Discarding` / `Skipping` / `persist failed` / `publish failed` | **0** |

El notify no es el hueco. El bus sí recibe el hecho; el persist no corre.

## Qué sí está cableado

- `REDIS_ENABLED=true`, perfil `prod`, `gigafiber.redis.namespace=prod`.
- `AcsToGatewayInformClient` XADD directo (`producer=acs`) porque el EventBus no es no-op. `ACS_GATEWAY_BASE_URL` unset no importa en este camino.
- Stream vivo: `prod:gigafiber.events` `XLEN≈20022` (techo `stream-maxlen=20000`).
- En la ventana retenida: **3228** `cpe.inform` (`producer=acs`), último `2026-09-18T02:12:36Z`. También `traffic.latest=15694`, `onu.state=881`, `onu.optical-batch=220`.

`gigafiber.events` sin prefijo está muerto (último id `2026-09-05T15:30:45Z`, lag 0). No es el que lee prod.

## Causa

Grupo `snapshot-core` sobre `prod:gigafiber.events`:

| Campo | Valor |
|-------|--------|
| `lag` | **20022** (toda la ventana) |
| `last-delivered-id` | `1789692045362-1` = **2026-09-18T00:40:45Z** |
| `pending` | ~29–31 |
| Avance en ~70 s | **ninguno** (mismo last-id) |
| `cpe-provision-core` | lag 0 (otro grupo; no persiste series) |

El hilo `gigafiber-redis-snapshot-core` está dentro de `HealthSnapshotIngestService.apply`: cada `traffic.latest` y cada ONU de un `onu.optical-batch` llama `summaries.reevaluate`. En 2 h: **1230** `Slow reevaluate` (1008 `traffic.latest`, 216 `onu.optical-batch`, 6 `onu.state`), 400–2300 ms cada uno.

`RedisStreamPump.poll` lee 50, entrega en serie, ACK al terminar el handler. Un `onu.optical-batch` de flota × ~1 s por suscripción deja el cursor clavado. Mientras tanto el XADD con `MAXLEN≈20000` recorta lo no leído. Los `cpe.inform` de ACS entran y salen del stream sin persistir.

Llegada ≈ 15 evt/s (tráfico + Informs). Consumo efectivo ≪ 2 evt/s cuando hay reevaluate lento. No converge.

## No es

- Ext mal cableado (ACS 200 + last-state fresco).
- Namespace ACS≠Core (ambos `prod:`).
- Persist que descarta SN/`complete=false` (no hay esos logs; el consumer no llega al `cpe.inform`).
- HTTP ACS→Gateway: con Redis on no se usa.

## Qué habría que cambiar (no hecho aquí)

Separar el persist de `cpe.inform` del reevaluate 360 de `traffic.latest` / `onu.optical-batch` (otro grupo, o no reevaluar en esos tipos). Subir `maxlen` solo tapa el recorte; no desatasca el hilo.
