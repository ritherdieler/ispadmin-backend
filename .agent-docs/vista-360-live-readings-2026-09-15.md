# Vista 360 — lecturas en vivo (2026-09-15)

`GET /subscription/{id}/live-readings` en Core. WS y GET de interfaces incluyen `pppoe-in` + `rxBytes`/`txBytes`.

Staging y prod comparten MK2. La cola `id:6` es Cintia Escobal (prod, `192.168.30.23`). El lab #6 es `<pppoe-gf6>` (`*89C`). El matcher prioriza el nombre PPPoE y solo acepta `id:{n}` si el tag de ambiente coincide (`stg` vs prod vacío).

## Contrato

Path desplegado: `/ispadmin-staging/subscription/{id}/live-readings` (staging) o `/ispadmin/subscription/{id}/live-readings` (local/prod). Auth igual que el resto de `/subscription/**`. Siempre 200: sin host/IP/sesión → `available: false`, `source: NONE`.

| source | Cómo | bps | bytes | pppoe |
|--------|------|-----|-------|-------|
| `QUEUE` | `/queue/simple` por IP, `pppoeLastIp`, nombre `<pppoe-{user}>` o `id:{subscriptionId}` | `rate` upload/download | `bytes` upload/download | `null` |
| `PPPOE` | fallback `/interface` type `pppoe-in` | 0 | `rx-byte` / `tx-byte` | user extraído |
| `NONE` | nada de lo anterior | 0 | 0 | `null` |

`rate`/`bytes` MikroTik = `upload/download`. El DTO mapea download = 2.º, upload = 1.º.

Validar #6 (`pppoe:gf6`): cola `<pppoe-gf6>` o `id:6`; si no hay cola, interfaz `<pppoe-gf6>`.

`SubscriptionLiveReadingService` tiene un solo constructor Spring (`repository` + `MikroTikConnectionService`). Un constructor secundario de `Clock` rompe el arranque en Tomcat (`No default constructor`).

## Tests

13 JUnit/MockK en `:core`: `SubscriptionLiveReadingServiceTest`, `SubscriptionLiveReadingControllerTest`, `NetworkDeviceConnectionServiceTest`, `InterfaceTrafficMapperTest`.
