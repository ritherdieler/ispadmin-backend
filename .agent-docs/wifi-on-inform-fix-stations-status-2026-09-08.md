# WiFi-on-Inform — fix stations + status_current (2026-09-08)

**Canónico (cómo funciona):** [wifi-on-inform-flujo-acs-gateway-core.md](./wifi-on-inform-flujo-acs-gateway-core.md). Esta nota documenta el pitfall del lookup `Instant` tras upsert.

## Problema

Tras E2E staging PASS de count samples (`acs_wifi_count_sample` id=3422, sub 2389, 2g=2/5g=1):

| Tabla | Estado |
|-------|--------|
| `acs_wifi_station_sample` | vacío |
| `acs_wifi_status_current` | no refrescado |

## Causa raíz

En `CpeInformPersistService.persist`, tras `upsertAtomic` el id del count sample se resolvía solo con:

`findByDeviceIdAndSubscriptionIdAndObservedAt(deviceId, subscriptionId, Instant)`

Ese lookup JPA por `Instant` falla con frecuencia frente a la fila insertada por SQL nativo (`UtcInstantText` / precisión DATETIME MySQL). El servicio hacía `?: return` **antes** de guardar stations y `acs_wifi_status_current`. El count sample sí quedaba.

## Fix

Resolver el id con `AcsWifiSampleLookup.idAfterUpsert`:

1. `findIdByDeviceIdAndSubscriptionIdAndObservedAtSql` (mismo string UTC que el upsert)
2. fallback JPA `findBy…ObservedAt` → `id`
3. fallback `findTopByDeviceIdAndSubscriptionIdOrderByInformAtDesc` / `findIdBy…InformText`

Sin early-return silencioso tras el upsert.

## Tests

`CpeInformPersistServiceTest`:

- `stations and status current persist when JPA observedAt lookup misses after upsert`
- `falls back to latest count sample id when observed lookups both miss`

Suite focal: `CpeInformPersistServiceTest`, `CpeInformIngestWiringTest`, `HealthSqlTimeTest` → BUILD SUCCESS (10 tests).

## Deploy / revalidación

- Solo Core staging (`--with servicehealth,netdiag,observability --only core`); ACS/Gateway no cambian el shape del payload.
- WAR Core: strip DJL de `WEB-INF/lib` (deben vivir en `CATALINA_HOME/lib`); health `/ispadmin-staging/` → HTTP 200.

### Revalidación VSOL (2026-09-08 tarde) — **PASS**

| Paso | Resultado |
|------|-----------|
| CR GenieACS NBI | HTTP **200**; `_lastInform` `21:52:23Z` → `21:53:43Z` |
| Auto `ext` wifi-inform-notify | Sin rastro en logs GenieACS en esa ventana (no bloquea el fix Core) |
| `POST …/inform-notify` (mismo contrato que el ext) | HTTP **200** `COMPLETE` |
| `acs_wifi_count_sample` | **id=3424**, sub **2389**, 2g=2 / 5g=0 / total=2, `FRESH`, `inform_at=2026-09-08 21:53:43` (alineado al Inform del CR) |
| `acs_wifi_station_sample` | **2** filas en count **3424**, bandas `2.4` |
| `acs_wifi_status_current` | device VSOL, `associated=2`, `count_sample_id=3424`, `observed_at=21:53:43`, `updated_at=21:56:07` |

Antes del fix (id=3422): stations vacío y `status_current` sin refrescar. Tras fix: stations + status_current OK.

Evidencia E2E previa (count-only): [wifi-on-inform-validacion-staging-2026-09-08.md](./wifi-on-inform-validacion-staging-2026-09-08.md).
