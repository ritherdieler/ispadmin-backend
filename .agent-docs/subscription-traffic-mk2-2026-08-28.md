# Consumo de tráfico — origen MK2

**Fecha:** 2026-08-28

## Origen de datos

El consumo por suscripción se obtiene del **MikroTik host** (`subscription.host_device_id`), no de SmartOLT ni de seed local.

| Dispositivo | ID BD | Nombre | IP |
|-------------|-------|--------|-----|
| **MK2** | `8` | Mikrotik CCR 2 | `38.224.231.4` |
| MK1 (legacy) | `1` | Mikrotik CCR | `38.224.231.2` |

Ejemplo suscripción #1 (Tomasa Egusquiza): IP `192.168.26.65` → cola simple en **MK2**.

## Flujo de recolección

1. Scheduler cada **5 min** (`traffic.poll.interval-ms=300000`).
2. `SubscriptionTrafficPollService` agrupa suscripciones por `hostDevice`.
3. Conecta vía **RouterOS REST API** al MK2 (`/queue/simple` + `/system/resource`).
4. Empareja cola por IP del cliente y persiste delta en `subscription_traffic_sample`.

## Mock vs real

- `mikrotik.connection.mock.enabled=true` en dev afecta **provisión** y otros flujos.
- El poll de tráfico usa el bean **`trafficPollMikrotikClient`** (siempre RouterOS real, sin mock).
- **2026-08-28:** en dev, `NetworkDeviceConnectionHelper` redirigía el host a `mikrotik_test` (`192.168.1.100`) → timeout. El poll ahora usa `applyDevConnectionOverride=false` y conecta al MK2 real (`38.224.231.4`).

## Demo local con consumo real

1. Backend `dev,local` levantado; poll manual o scheduler cada 5 min.
2. Cliente de prueba: suscripción **#615** (`192.168.25.92`, cola en MK2).
3. Backoffice: `http://127.0.0.1:3001/subscriptions?subscriptionId=615` → pestaña **Hoy** / **7 días**.

```bash
curl -s -X POST http://127.0.0.1:8080/ispadmin/traffic/poll -H "Authorization: Bearer $TOKEN"
# devicesPolled=1, subscriptionsMatched≈800, samplesWritten>0
```

## Poll manual

```http
POST /ispadmin/traffic/poll
Authorization: Bearer {token}
```

Respuesta: `devicesPolled`, `subscriptionsMatched`, `samplesWritten`, `error`.

## Seed local (solo demo UI)

`TrafficLocalSeedIntegrationTest` genera datos sintéticos. **No sustituye** al MK2 en producción ni en validación operativa.

```bash
sh mvnw test -Dtest=TrafficLocalSeedIntegrationTest
```

## Correcciones 2026-08-28

- `@NotFound(IGNORE)` en `Subscription.fiberOnu` — evita fallo JPA si la ONU referenciada no existe.
- `getSeries` usa `takeLast(500)` para devolver los puntos más recientes.
- Bean dedicado `trafficPollMikrotikClient` para forzar conexión real al MK2.

## Requisitos de red

El backend debe poder alcanzar `38.224.231.4` (REST RouterOS, puerto configurado en `router.os.client`). Desde laptop local puede haber TIMEOUT si no hay ruta/VPN al borde.
