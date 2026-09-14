# Nombres de estaciones Wi‑Fi en 360 (2026-08-31)

## Cambio

La tabla **Estaciones conectadas** del 360 muestra el **nombre del dispositivo** (`Hosts.HostName` / `AssociatedDeviceName` del ACS), no solo el id opaco HMAC.

| Capa | Detalle |
|------|---------|
| ACS | Proyección de `Hosts.Host.{i}.HostName` + `MACAddress` (hasta 64) al leer estaciones; fallback `AssociatedDeviceName` / `X_ZTE-COM_AssociatedDeviceName` |
| Persistencia | Columna `acs_wifi_station_sample.display_name` (Flyway `V37`) |
| API | `GET .../service-health/series` → `wifi_signal[].display_name` |
| UI | Columna **Dispositivo**; si no hay nombre, id corto de 8 hex. La MAC no se expone |

## Connection Request (CR) — obligatorio cuando haga falta

Siempre que se necesite **datos frescos del CPE** (nombres Hosts, RSSI por estación, conteos WLAN, validar un fix de telemetría), hay que encolar **GPV + Connection Request**. Sin CR el watcher solo lee caché GenieACS y puede quedarse con hojas viejas o incompletas.

| Cuándo | Qué hacer |
|--------|-----------|
| Tras deploy de cambios ACS/Wi‑Fi | CR + GPV (totales + Hosts + AssociatedDevice) y esperar el poll (~2 min) |
| UI 360 sin nombres / pocas estaciones vs conteo | Igual: purgar tasks/faults stale del device, luego GPV `?connection_request` |
| Validación lab #2329 | Device `B46415-V2804AX15T-12345B4641531C0B6` vía NBI `127.0.0.1:7557` en el VPS |
| Alternativa producto | `POST .../acs/wifi-refresh` con `X-Confirm-Action` + `Idempotency-Key` (respeta cooldown 15 min de muestra) |

Antes del CR: borrar tasks/faults pendientes del device (p. ej. un `refreshObject` viejo bloquea la cola). Ejemplo NBI:

```bash
# En el VPS (GenieACS NBI en 127.0.0.1:7557)
# POST /devices/{urlencodedId}/tasks?connection_request
# body: {"name":"getParameterValues","parameterNames":[...Hosts...,...AssociatedDevice...]}
```

## Deploy staging

```bash
./scripts/deploy.sh --env staging --with servicehealth
```

Tras el deploy: **hacer CR** (arriba) y esperar muestra ACS nueva. Dispositivos sin hostname en el CPE seguirán con id corto.

## Por qué la tabla no iguala “LAN hosts” de GenieACS

- **Hosts** (p. ej. 9) = clientes LAN cableados + Wi‑Fi.
- **Estaciones 360** = solo `WLAN AssociatedDevice` con RSSI/SNR (Wi‑Fi asociado).
- En VSOL `V2804AX15T`, `stationProjection` debe mapear conteos por **banda** (radio 1 = 5 GHz, radio 5 = 2.4). Un bug previo asignaba `associated2g` al radio 1 y dejaba solo ~2 filas aunque el conteo fuera 4.

## Verificación (staging #2329)

Muestra **#1223** tras fix + CR: 4 estaciones — `S25-Ultra-de-Sergio-c`, `Mac`, `Samsung` (5 GHz) y una en 2.4 sin HostName emparejado (`?` / id corto).
