# Vista 360 — lecturas en vivo (2026-09-15)

`GET /subscription/{id}/live-readings` en Core (consulta puntual). El gráfico **Ahora** de la 360 no usa este GET: va por socket STOMP ([vista-360-live-socket-static-ip-2026-09-15.md](./vista-360-live-socket-static-ip-2026-09-15.md)). WS y GET de interfaces incluyen `pppoe-in` + `rxBytes`/`txBytes`.

Staging y prod comparten MK2. La cola `id:6` (`*9`, Cintia Escobal, `192.168.30.23`) no es el lab. El lab #6 es `PPPOE_DYNAMIC` `gf6`: interfaz `pppoe-in` `<pppoe-gf6>` (y cola dinámica `*89C` solo como objeto residual).

## Contrato

Path desplegado: `/ispadmin-staging/subscription/{id}/live-readings` (staging) o `/ispadmin/subscription/{id}/live-readings` (local/prod). Auth igual que el resto de `/subscription/**`. Siempre 200: sin host/sesión → `available: false`, `source: NONE`.

| source | Cuándo | Cómo | bps | bytes | pppoe |
|--------|--------|------|-----|-------|-------|
| `PPPOE` | `PPPOE_DYNAMIC` | `/interface` type `pppoe-in` + `/interface/monitor-traffic once` | `tx-bits-per-second` = download, `rx-bits-per-second` = upload | `tx-byte` = rxBytes (hacia el cliente), `rx-byte` = txBytes | user extraído |
| `QUEUE` | `STATIC_IP` / `PPPOE_FIXED` | `/queue/simple` por IP, `pppoeLastIp`, nombre `<pppoe-{user}>` o `id:{subscriptionId}` con tag de ambiente | `rate` upload/download | `bytes` upload/download | `null` |
| `NONE` | nada de lo anterior | — | 0 | 0 | `null` |

`PPPOE_DYNAMIC` no lee `rate` de simple queue. En `pppoe-in` el RX del router es upload del cliente y el TX es download.

`SubscriptionLiveReadingService` tiene un solo constructor Spring (`repository` + `MikroTikConnectionService` + `GigafiberEnvironmentProperties`). `Clock` es `internal var`. `MikroTikConnectionService.callOnDevice` delega a `MikrotikSession.call`.

## Tests

JUnit/MockK en `:core`: `SubscriptionLiveReadingServiceTest`, `SubscriptionLiveReadingControllerTest`, `MikroTikConnectionServiceTest`, `NetworkDeviceConnectionServiceTest`, `InterfaceTrafficMapperTest`.
