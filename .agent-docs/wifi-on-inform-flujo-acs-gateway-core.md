# WiFi-on-Inform — flujo ACS → Gateway → Core

**Doc canónico** (cómo funciona el camino). Las notas con fecha son evidencia operativa; no sustituyen este documento.

Desacople de transporte (HTTP / Redis Streams; ACS **no** XADD): [subsistemas-desacople-transporte.md](./subsistemas-desacople-transporte.md).  
Secretos (solo nombres): [vps-secrets-management.md](./vps-secrets-management.md).  
Diagramas sin cruces: [diagramas-arquitectura-orden.md](./diagramas-arquitectura-orden.md).

## 1. Flujo extremo a extremo

```text
Capas (arriba → abajo). Continua = HTTP/CWMP. Punteada = Redis Streams.

[Dispositivo]          [ACS stack]                 [Bus]              [Core]                 [UI]

ONU (VSOL lab)
 │ CWMP Inform
 ▼
GenieACS
 │ declare WiFi + ext wifi-inform-notify
 │ (HTTP, header X-Acs-Key)
 ▼
ACS WAR
 │ parse NBI → last-state en cpe_record
 │ (HTTP, header X-Acs-To-Gateway-Key)
 ▼
Gateway
 │ POST ingest → XADD ···············► Redis
 │   type=cpe.inform                     │
 │                                       ▼
 │                                 Core consumer
 │                                 (service-health)
 │                                       │
 │                                       ▼
 │                                 acs_wifi_* tables
 │                                 (count / stations /
 │                                  status_current)
 │                                       │
 │                                       │ GET series 360
 │                                       ▼
 └───────────────────────────────────── Backoffice
                                         WifiCharts
```

Clientes externos (backoffice, app) hablan **solo con Core**. Core no llama a GenieACS ni al ACS WAR por este camino: consume el hecho `cpe.inform` desde Redis.

### Pasos numerados

| # | Pieza | Qué hace |
|---|--------|----------|
| 1 | ONU | Envía CWMP Inform (PERIODIC, CONNECTION REQUEST u otro evento). |
| 2 | GenieACS | Provision piloto `gigafiber-wifi-telemetry`: `ext("wifi-inform-notify", …)` **antes** de los `declare` WLAN; luego refresca TotalAssociations / AssociatedDevice. |
| 3 | Ext `wifi-inform-notify.js` | `POST /api/acs/v1/cpe/inform-notify` al ACS WAR. |
| 4 | ACS `WifiInformNotifyService` | Lee caché NBI, parsea `WifiNbiTelemetry`, upsert **last-state** en `cpe_record`, POST al Gateway. |
| 5 | Gateway `CpeInformIngestService` | Publica `PlatformEventTypes.CPE_INFORM` (`cpe.inform`) en Redis Streams. No persiste series. |
| 6 | Core `HealthSnapshotIngestService` → `CpeInformPersistService` | Idempotente; escribe `acs_wifi_count_sample`, `acs_wifi_station_sample`, `acs_wifi_status_current`; y `telemetry_source_run` (`source=ACS`, `equipmentKey=gateway-cpe`) para el collector 360. |
| 7 | Backoffice 360 | `GET …/service-health/series` → `WifiCharts` / estaciones. |

Poll Core `AcsTelemetryService` / `SERVICE_HEALTH_ACS_POLL_ENABLED`: default **false** cuando este consumer está cableado. `ACS/collector` del 360 no depende de ese poll: se refresca con cada Inform persistido (misma clave `gateway-cpe` que miraba el poller).

## 2. Qué significa “Inform”

En TR-069, un **Inform** es la sesión CWMP en la que la ONU reporta eventos al ACS. El código de evento típico va en `Event.EventStruct[].EventCode`.

| Evento (ejemplos) | Origen | ¿Dispara el provision piloto? |
|-------------------|--------|-------------------------------|
| `2 PERIODIC` | Reloj de la ONU (`PeriodicInformInterval`) | Sí |
| `6 CONNECTION REQUEST` | ACS/GenieACS pidió CR (Summon / task) | Sí |
| `1 BOOT`, `0 BOOTSTRAP`, `4 VALUE CHANGE`, otros | Boot, bootstrap, cambios, etc. | Sí (si hay sesión Inform) |

El preset piloto usa canal `inform` con `events: {}`: **cualquier** Inform del allowlist ejecuta el script (no solo `2 PERIODIC`).

`PeriodicInformInterval` (piloto **180 s**) **solo agenda** Informs de tipo PERIODIC. No limita CONNECTION REQUEST ni otros eventos: un CR puede producir un Inform inmediato aunque el intervalo PERIODIC no haya vencido.

## 3. Alcance del piloto (VSOL lab)

| Campo | Valor |
|-------|--------|
| GenieACS `_id` | `B46415-V2804AX15T-12345B4641531C0B6` |
| Serial | `12345B4641531C0B6` |
| ProductClass | `V2804AX15T` |
| Tag | `lab` |
| Suscripción staging (evidencia) | `subscription_id=2389` |
| `PeriodicInformInterval` | **180 s** solo ese serial (declare en el provision piloto; bootstrap de flota sigue en 3600) |
| Radios VSOL | WLAN **1** (5 GHz), WLAN **5** (2.4 GHz) |

No se toca el preset/provision global `inform` / `inform.js` de flota. Allowlist = Serial + ProductClass del `_id` anterior.

Detalle apply/rollback: [piloto-wifi-on-inform-vsol-lab-2026-09-08.md](./piloto-wifi-on-inform-vsol-lab-2026-09-08.md).

## 4. Responsabilidades por capa

| Capa | Hace | No hace |
|------|------|---------|
| GenieACS provision + ext | Declarar WiFi fresco; notificar ACS por HTTP | Persistir series Core; XADD Redis |
| ACS WAR | Parse NBI; **last-state** en `cpe_record`; forward HTTP al Gateway | XADD Redis; escribir `acs_wifi_*` del Core |
| Gateway | Ingest HTTP → **solo** XADD `cpe.inform` | Parse WiFi profundo; tablas de series |
| Core service-health | Consumer Redis; persistir series + `status_current` | Hablar GenieACS/ACS WAR en este camino |
| Backoffice | Leer series vía Core | Llamar ACS/Gateway/GenieACS |

**Invariante:** ACS **nunca** publica Redis. Gateway es el productor de `cpe.inform` / `cpe.provisioning`.

## 5. Auth / secretos (solo nombres)

| Variable | Rol | Dónde vive (típico) |
|----------|-----|---------------------|
| `GENIEACS_TO_ACS_API_KEY` | Ext → ACS (`X-Acs-Key`; endpoint también acepta `ACS_API_KEY`) | `/opt/gigafiber/genieacs/.env` + runtime Tomcat ACS |
| `GENIEACS_TO_ACS_NOTIFY_URL` | Base URL ACS vista desde GenieACS | `/opt/gigafiber/genieacs/.env` |
| `ACS_TO_GATEWAY_API_KEY` | ACS → Gateway (`X-Acs-To-Gateway-Key`) | Pareja ACS ↔ Gateway |
| `ACS_GATEWAY_BASE_URL` | Base URL Gateway desde ACS → `acs.gateway.internal-base-url` | **Hornear por WAR**; no `.env` compartido |
| `SERVICE_HEALTH_ACS_POLL_ENABLED` | Poll legado Core; default `false` con consumer Inform | `/opt/gigafiber/.env` / properties |

Catálogo: [vps-secrets-management.md](./vps-secrets-management.md). No documentar ni commitear valores.

Tras rotar `GENIEACS_TO_ACS_API_KEY`, recrear GenieACS **y** Tomcat si uno quedó con el valor viejo en memoria (401 en auto-ext).

## 6. Idempotencia

En Core (`CpeInformPersistService`):

1. Resuelve `subscriptionId` por SN (`IdentityService.resolveOnu`).
2. Si ya existe fila con **`deviceId` + `subscriptionId` + `informAt`**, sale sin reescribir.
3. Upsert atómico de count sample con `informAt` / `observedAt` (texto UTC).
4. Stations y `acs_wifi_status_current` se enganchan al id del count sample vía `AcsWifiSampleLookup.idAfterUpsert` (no confiar solo en lookup JPA por `Instant`).

Reentregas Redis del mismo Inform no duplican samples. SN desconocido o payload incompleto / `UNSUPPORTED` → skip (log), sin fallar el consumer.

## 7. Pitfalls conocidos

| Pitfall | Efecto | Mitigación |
|---------|--------|------------|
| `try/catch` vacío alrededor de `declare` / `ext` en GenieACS | Traga Symbols `EXT`/`COMMIT`; el auto-notify no se programa | **Nunca** try/catch ahí; ver [wifi-inform-auto-ext-vsol-2026-09-08.md](./wifi-inform-auto-ext-vsol-2026-09-08.md) |
| `ext` **después** de muchos `declare` WLAN | Notify depende de GPV largos / `too_many_commits` | `ext` **antes** de declares WLAN (código actual) |
| Lookup JPA `Instant` justo tras upsert SQL | Count sí; stations/`status_current` no (early return) | `AcsWifiSampleLookup.idAfterUpsert` ([fix stations](./wifi-on-inform-fix-stations-status-2026-09-08.md)) |
| UI `WifiCharts` formatea ticks en **HH:mm** (`formatHealthTime`) | Varios samples en el mismo minuto colapsan visualmente el eje | Tooltip / rango; no asumir un tick = un sample |
| `refreshObject` de `WLANConfiguration` en ráfaga | GenieACS `too_many_commits`; sesión CWMP saturada | Cooldown GPV; preferir declare en Inform + notify; no refrescar WLAN en bucle |
| Keys GenieACS ↔ Tomcat desalineadas | Auto-ext 401; Inform sí, Core no | Sync + recreate contenedores |
| Poll Core encendido en paralelo | Doble camino / ruido | `SERVICE_HEALTH_ACS_POLL_ENABLED=false` |

## 8. Artefactos de código (referencia)

| Pieza | Ubicación |
|-------|-----------|
| Provision | `scripts/genieacs/provisions/gigafiber-wifi-telemetry.js` |
| Ext | `scripts/genieacs/ext/wifi-inform-notify.js` |
| Apply | `scripts/genieacs/apply-wifi-telemetry.py` |
| ACS notify | `WifiInformNotifyService`, `POST /api/acs/v1/cpe/inform-notify` |
| Gateway ingest | `CpeInformIngestService`, `POST /api/olt-gateway/acs/cpe-inform` |
| Core persist | `CpeInformPersistService` / `HealthSnapshotIngestService` |
| Evento | `PlatformEventTypes.CPE_INFORM` = `cpe.inform` |

## 9. Evidencia y notas fechadas (no canónicas)

| Nota | Contenido |
|------|-----------|
| [piloto-wifi-on-inform-vsol-lab-2026-09-08.md](./piloto-wifi-on-inform-vsol-lab-2026-09-08.md) | Allowlist, 180 s, apply/rollback |
| [wifi-on-inform-cableado-2026-09-08.md](./wifi-on-inform-cableado-2026-09-08.md) | Resumen corto del cableado (apunta aquí) |
| [wifi-on-inform-validacion-staging-2026-09-08.md](./wifi-on-inform-validacion-staging-2026-09-08.md) | E2E staging PASS (count + Redis) |
| [wifi-on-inform-fix-stations-status-2026-09-08.md](./wifi-on-inform-fix-stations-status-2026-09-08.md) | Fix lookup post-upsert; stations + status |
| [wifi-inform-auto-ext-vsol-2026-09-08.md](./wifi-inform-auto-ext-vsol-2026-09-08.md) | try/catch Symbols; auto-ext PASS |

Nota histórica previa al push (series vacías por stub poll): [diagnostico-wifi-estaciones-vacias-staging-2026-09-08.md](./diagnostico-wifi-estaciones-vacias-staging-2026-09-08.md) — **superseded** para el camino Inform; el ingest activo es el de este doc.
