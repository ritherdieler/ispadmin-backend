# Vista 360 — lecturas en vivo (2026-09-15)

`GET /subscription/{id}/live-readings` en Core. WS y GET de interfaces incluyen `pppoe-in` + `rxBytes`/`txBytes`.

## Contrato

Path desplegado: `/ispadmin-staging/subscription/{id}/live-readings` (staging) o `/ispadmin/subscription/{id}/live-readings` (local/prod). Auth igual que el resto de `/subscription/**`. Siempre 200: sin host/IP/sesión → `available: false`, `source: NONE`.

| source | Cómo | bps | bytes | pppoe |
|--------|------|-----|-------|-------|
| `QUEUE` | `/queue/simple` por IP, `pppoeLastIp`, nombre `<pppoe-{user}>` o `id:{subscriptionId}` | `rate` upload/download | `bytes` upload/download | `null` |
| `PPPOE` | fallback `/interface` type `pppoe-in` | 0 | `rx-byte` / `tx-byte` | user extraído |
| `NONE` | nada de lo anterior | 0 | 0 | `null` |

`rate`/`bytes` MikroTik = `upload/download`. El DTO mapea download = 2.º, upload = 1.º.

Validar #6 (`pppoe:gf6`): cola `<pppoe-gf6>` o `id:6`; si no hay cola, interfaz `<pppoe-gf6>`.

## Tests

13 JUnit/MockK en `:core`: `SubscriptionLiveReadingServiceTest`, `SubscriptionLiveReadingControllerTest`, `NetworkDeviceConnectionServiceTest`, `InterfaceTrafficMapperTest`.
