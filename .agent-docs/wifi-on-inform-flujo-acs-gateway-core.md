# WiFi-on-Inform — flujo ACS → Gateway → Core

**Doc canónico** (cómo funciona el camino). Las notas con fecha son evidencia operativa; no sustituyen este documento.

Cadencia lab 60 s y persist 1:1 sin throttle Core: [wifi-inform-clock-una-perilla-2026-09-15.md](./wifi-inform-clock-una-perilla-2026-09-15.md).

## 0. Principio: el Inform es la única fuente de verdad

El Inform de cada ONU, al intervalo que tenga configurado, es la única fuente de
verdad para todo lo que consuma datos de ONUs. No hay polls, ni sweeps, ni GPV
de lectura en paralelo. Una acción manual **no lee**: fuerza un Connection
Request, que produce un Inform, que entrega el dato por el camino normal.

De ahí salen tres consecuencias que contradicen versiones anteriores de este doc:

- **El `ext` va después de los `declare`**, no antes, porque ahora lleva los
  valores en el payload en vez de pedirle al ACS que lea el NBI.
- **El ACS no lee el NBI** en el camino feliz. GenieACS hace commit del árbol de
  parámetros al cerrar la sesión, así que cualquier lectura del NBI durante la
  sesión ve la sesión *anterior*. El payload del `ext` elimina ese desfase por
  construcción. El `readDeviceCache` queda solo como fallback.
- **`PeriodicInformInterval` es la única perilla de cadencia viva.** Vive en
  `gf-inform-interval` (canal `inform`, cada sesión). Lab **60 s**; flota **1800 s** +
  jitter. El preset `bootstrap` es solo `0 BOOTSTRAP` (factory): `clear()` +
  credenciales. Un reboot (`1 BOOT` / `M Reboot`) no corre bootstrap; el
  intervalo nuevo se toma en el próximo Inform. Core persiste **cada** `cpe.inform`
  completo (idempotente por `informAt`); no copia el intervalo ni filtra por hueco.

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
 │ declare WiFi, luego ext wifi-inform-notify(payload)
 │ (HTTP, header X-Acs-Key)
 ▼
ACS WAR
 │ parse payload → last-state en cpe_record
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
| 2 | GenieACS | Provision `gigafiber-wifi-telemetry`: refresca TotalAssociations / AssociatedDevice / Hosts con wildcards contra el reloj de la sesión y **después** llama `ext("wifi-inform-notify", serial, deviceId, payload)` con las hojas crudas. |
| 3 | Ext `wifi-inform-notify.js` | `POST /api/acs/v1/cpe/inform-notify` al ACS WAR con el payload en el body. Timeout 2500 ms, por debajo del `EXT_TIMEOUT` de GenieACS (3000 ms). |
| 4 | ACS `WifiInformNotifyService` | `WifiNbiTelemetry.expandInformLeaves(payload)` → `parsePayload`; upsert **last-state** en `cpe_record`; POST al Gateway. Sin payload, cae al `readDeviceCache`. |
| 5 | Gateway `CpeInformIngestService` | Publica `PlatformEventTypes.CPE_INFORM` (`cpe.inform`) en Redis Streams. No persiste series. |
| 6 | Core `HealthSnapshotIngestService` → `CpeInformPersistService` | Persistencia si hay SN mapeado; idempotente por `informAt`; escribe `acs_wifi_count_sample`, `acs_wifi_station_sample` (batch JDBC, `observedAt` = `informAt`) y `acs_wifi_status_current`. |
| 7 | Backoffice 360 | `GET …/service-health/series` → `WifiCharts` / estaciones. |

`ACS/collector` del 360 sale de `acs_wifi_status_current.observedAt` de la suscripción,
con umbral `service.health.acs-wifi-sample-target-seconds * 2`. **Ya no se inserta
`telemetry_source_run` por Inform**: no hay run, y la clave global `equipmentKey=gateway-cpe`
tapaba el staleness por dispositivo.

En **local-prestaging** el ext de GenieACS sigue pegando al ACS del VPS. Para llenar Redis `lpstg` hay que `POST /api/acs/v1/cpe/inform-notify` al WAR local (`./scripts/run-local-prestaging.sh inform-notify`).

## 2. Qué significa “Inform”

En TR-069, un **Inform** es la sesión CWMP en la que la ONU reporta eventos al ACS. El código de evento típico va en `Event.EventStruct[].EventCode`.

| Evento (ejemplos) | Origen | ¿Dispara el provision piloto? |
|-------------------|--------|-------------------------------|
| `2 PERIODIC` | Reloj de la ONU (`PeriodicInformInterval`) | Sí |
| `6 CONNECTION REQUEST` | ACS/GenieACS pidió CR (Summon / task) | Sí |
| `1 BOOT`, `0 BOOTSTRAP`, `4 VALUE CHANGE`, otros | Boot, bootstrap, cambios, etc. | Sí (si hay sesión Inform) |

El preset piloto usa canal `inform` con `events: {}`: **cualquier** Inform del allowlist ejecuta el script (no solo `2 PERIODIC`).

`PeriodicInformInterval` (**1800 s + jitter derivado del serial**) **solo agenda** Informs de tipo PERIODIC. No limita CONNECTION REQUEST ni otros eventos: un CR puede producir un Inform inmediato aunque el intervalo PERIODIC no haya vencido.

## 3. Alcance del piloto (VSOL lab)

| Campo | Valor |
|-------|--------|
| GenieACS `_id` | `B46415-V2804AX15T-12345B4641531C0B6` |
| Serial | `12345B4641531C0B6` |
| ProductClass | `V2804AX15T` |
| Tag | `lab` |
| Suscripción staging (evidencia) | `subscription_id=2389` |
| `PeriodicInformInterval` | Lab: **60 s** (serial de banco). Flota: **1800 s + jitter**. Todo en `gigafiber-bootstrap.js` / `gf-inform-interval.js`; el provision de telemetría ya no escribe cadencia |
| Radios VSOL | WLAN **1** (5 GHz), WLAN **5** (2.4 GHz) |

No se toca el preset/provision global `inform` / `inform.js` de flota. Allowlist = Serial + ProductClass del `_id` anterior.

Detalle apply/rollback: [piloto-wifi-on-inform-vsol-lab-2026-09-08.md](./piloto-wifi-on-inform-vsol-lab-2026-09-08.md).

## 4. Responsabilidades por capa

| Capa | Hace | No hace |
|------|------|---------|
| GenieACS provision + ext | Declarar WiFi fresco y serializar las hojas al `ext` | Decidir qué es válido o fresco; persistir series; XADD Redis |
| ACS WAR | Parsear el payload (rangos, MAC, quality, `complete`); **last-state** en `cpe_record`; forward HTTP al Gateway | XADD Redis; escribir `acs_wifi_*` del Core; leer el NBI salvo fallback |
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

Catálogo: [vps-secrets-management.md](./vps-secrets-management.md). No documentar ni commitear valores.

Tras rotar `GENIEACS_TO_ACS_API_KEY`, recrear GenieACS **y** Tomcat si uno quedó con el valor viejo en memoria (401 en auto-ext).

## 6. Idempotencia

En Core (`CpeInformPersistService`):

1. Resuelve `subscriptionId` por SN (`IdentityService.resolveOnu`).
2. Si ya existe fila con **`deviceId` + `subscriptionId` + `informAt`**, sale sin reescribir.
3. Upsert atómico de count sample con `informAt` / `observedAt` (texto UTC).
4. Stations se estampan con `observedAt = informAt` (el timestamp de hoja TR-069 solo sirve al gate `complete`). `acs_wifi_status_current` se engancha al id del count sample vía `AcsWifiSampleLookup.idAfterUpsert` (no confiar solo en lookup JPA por `Instant`).

Reentregas Redis del mismo Inform no duplican samples. SN desconocido o payload incompleto / `UNSUPPORTED` → skip (log), sin fallar el consumer.

## 7. Pitfalls conocidos

| Pitfall | Efecto | Mitigación |
|---------|--------|------------|
| `try/catch` vacío alrededor de `declare` / `ext` en GenieACS | Traga Symbols `EXT`/`COMMIT`; el auto-notify no se programa | **Nunca** try/catch ahí; ver [wifi-inform-auto-ext-vsol-2026-09-08.md](./wifi-inform-auto-ext-vsol-2026-09-08.md) |
| `ext` **antes** de los `declare` | El payload sale vacío y el ACS tiene que leer el NBI, que en esa sesión todavía tiene los valores de la anterior | `ext` **después** de los declares (código actual); el orden lo fija `wifi-inform-notify-ext.test.cjs` |
| Declarar índices fijos (`AssociatedDevice.1..32`, `Hosts.Host.1..64`) | Las instancias ausentes nunca resuelven y queman iteraciones de commit hasta `too_many_commits` | Wildcards para descubrir; acotar el payload a `TotalAssociations` en el provision y a `MAX_STATIONS` en Kotlin |
| Declarar el árbol que el CPE no expone (`Device.*` en un TR-098 como `productClass=IGD`) | `too_many_commits` en el canal, y un fault en el canal `inform` deja al equipo sin producir verdad | Probar `declare(root + ".ManagementServer.URL", {value: 1}).size` antes de declarar; medir con `scripts/genieacs/inform-channel-coverage.py` |
| Proyectar el nodo `WLANConfiguration.N` completo en el NBI | Devuelve `SSID` y `KeyPassphrase` en claro | Proyectar solo `.TotalAssociations` y `.AssociatedDevice` |
| Lookup JPA `Instant` justo tras upsert SQL | Count sí; stations/`status_current` no (early return) | `AcsWifiSampleLookup.idAfterUpsert` ([fix stations](./wifi-on-inform-fix-stations-status-2026-09-08.md)) |
| UI `WifiCharts` formatea ticks en **HH:mm** (`formatHealthTime`) | Varios samples en el mismo minuto colapsan visualmente el eje | Tooltip / rango; no asumir un tick = un sample |
| Leer con GPV / `refreshObject` en paralelo al Inform | Compite con la fuente única y devuelve la sesión anterior | `POST /cpe/{sn}/wifi-refresh` es **solo** un Connection Request que reencola el provision; no hay cooldown de GPV que mantener |
| Keys GenieACS ↔ Tomcat desalineadas | Auto-ext 401; Inform sí, Core no | Sync + recreate contenedores |
| Un solo hilo `snapshot-core` reevalúa tráfico/óptica y persiste `cpe.inform` | ACS last-state fresco; series Core viejas; lag = MAXLEN | Carril `wifi-inform-core` solo para Inform; `snapshot-core` persiste óptica/tráfico sin `reevaluate` (GET 360 recalcula a los 60 s). Ver [wifi-inform-consumer-lane-2026-09-18.md](./wifi-inform-consumer-lane-2026-09-18.md) |

## 8. Artefactos de código (referencia)

| Pieza | Ubicación |
|-------|-----------|
| Provision telemetría | `scripts/genieacs/provisions/gigafiber-wifi-telemetry.js` |
| Provisions de flota | `scripts/genieacs/provisions/{inform,default,gigafiber-bootstrap}.js` (fuente de verdad; los applies los leen de ahí) |
| Ext | `scripts/genieacs/ext/wifi-inform-notify.js` |
| Apply | `scripts/genieacs/apply-wifi-telemetry.py` (`--device-id` para el escalón, `--all-models` para la flota) |
| Cobertura / faults | `scripts/genieacs/inform-channel-coverage.py` |
| ACS notify | `WifiInformNotifyService`, `POST /api/acs/v1/cpe/inform-notify` |
| ACS → bus | `AcsToGatewayInformClient`: XADD `cpe.inform` si el EventBus no es no-op; si no, `POST /api/olt-gateway/acs/cpe-inform` |
| Gateway ingest | `CpeInformIngestService` (fallback HTTP) |
| Core persist Wi‑Fi | `CpeInformEventConsumer` (grupo `wifi-inform-core`) → `CpeInformPersistService` |
| Core óptica/tráfico | `HealthSnapshotConsumer` (grupo `snapshot-core`) → `HealthSnapshotIngestService` (sin `reevaluate`) |
| Evento | `PlatformEventTypes.CPE_INFORM` = `cpe.inform` |

## 9. Evidencia y notas fechadas (no canónicas)

| Nota | Contenido |
|------|-----------|
| [wifi-inform-consumer-lane-2026-09-18.md](./wifi-inform-consumer-lane-2026-09-18.md) | Carril `wifi-inform-core` + snapshot sin reevaluate en tráfico/óptica |
| [piloto-wifi-on-inform-vsol-lab-2026-09-08.md](./piloto-wifi-on-inform-vsol-lab-2026-09-08.md) | Allowlist, 180 s, apply/rollback |
| [wifi-on-inform-cableado-2026-09-08.md](./wifi-on-inform-cableado-2026-09-08.md) | Resumen corto del cableado (apunta aquí) |
| [wifi-on-inform-validacion-staging-2026-09-08.md](./wifi-on-inform-validacion-staging-2026-09-08.md) | E2E staging PASS (count + Redis) |
| [wifi-on-inform-fix-stations-status-2026-09-08.md](./wifi-on-inform-fix-stations-status-2026-09-08.md) | Fix lookup post-upsert; stations + status |
| [wifi-inform-auto-ext-vsol-2026-09-08.md](./wifi-inform-auto-ext-vsol-2026-09-08.md) | try/catch Symbols; auto-ext PASS |

Nota histórica previa al push (series vacías por stub poll): [diagnostico-wifi-estaciones-vacias-staging-2026-09-08.md](./diagnostico-wifi-estaciones-vacias-staging-2026-09-08.md) — **superseded** para el camino Inform; el ingest activo es el de este doc.
