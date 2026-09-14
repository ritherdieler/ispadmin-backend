# Convención lab ACS + validación Wi‑Fi staging (2026-08-31)

## Convención

Una ONU es usable en **staging** solo si cumple las dos marcas:

1. Tag GenieACS `lab` (no gestionado: no es `sub-` / `t:` / `c:`). Constante `GenieAcsSubscriptionTags.LAB`.
2. Columna `subscription_acs.lab = 1`, sincronizada desde `_tags` al etiquetar / upsert ACS.

Invariante (`gigafiber.environment.tag` / `ServiceHealthProperties.collects`):

| Ambiente | Recolecta / WIFI_REFRESH / CR |
|----------|-------------------------------|
| staging (`tag=stg`) | solo `subscription_acs.lab == true` |
| prod (tag vacío) | piloto de `.env` **y** salta `lab == true` |

No añadir devices de lab a `SERVICE_HEALTH_PILOT_*` del `.env` compartido: el watcher de prod los tomaría.

## Fixture validado

| Campo | Valor |
|-------|--------|
| Suscripción staging | **2329** |
| SN | `12345B4641531C0B6` |
| IP real | `192.168.88.99` (no entra al pool staging `192.168.250.0/24`; sin simple queue) |
| Device ACS | `B46415-V2804AX15T-12345B4641531C0B6` |
| Modelo | `V2804AX15T` (radio **1 = 5 GHz**, radio **5 = 2.4 GHz**; telemetría lee ambos; nombres vía `Hosts.HostName`) |

**Cableado completo** (suscripción, GenieACS/`lab`, OLT `VSOL0031C0B6`, cola MikroTik `[stg]`): [lab-usuario-prueba-cableado-2329.md](./lab-usuario-prueba-cableado-2329.md).

Prod no tiene fila de esta ONU ni telemetría ACS suya.

## Deploy de la prueba

```bash
./scripts/deploy.sh --env staging --with servicehealth
VERIFY_WITH_SUBSYSTEMS=servicehealth ./scripts/verify-war.sh
```

**Tras cualquier deploy o cambio de telemetría Wi‑Fi/ACS:** hacer **CR + GPV** al device lab (o `WIFI_REFRESH` confirmado). Sin Connection Request el poll solo ve caché GenieACS y la 360 puede quedar desfasada. Detalle: [acs-wifi-station-display-name-2026-08-31.md](./acs-wifi-station-display-name-2026-08-31.md#connection-request-cr--obligatorio-cuando-haga-falta).

`--with servicehealth` hornea, al final de `application-staging.properties` (última clave gana):

- `gigafiber.subsystems.servicehealth.enabled=true`
- `gigafiber.scheduling.enabled=true`
- `service.health.enabled=true`
- `service.health.acs-enabled=true`
- `service.health.actions-enabled=true`

El WAR no incluye `oltgateway`, `netdiag`, `traffic` ni `observability`. Tomcat del VPS es **JDK 11**: `Stream.toList()` tumba el contexto; `WebSocketConfig` usa `Collectors.toList()`.

## Resultado (2026-09-01)

- Smoke `GET /ispadmin-staging/` → 200.
- `GET /subscription/2329/service-health`: `identity.lab=true`, piloto/acciones on, ACS `last_inform` / `associated_device_count=2` / `wifi_signal rssi_min=-69` **FRESH**.
- Run ACS `3027`: `written=1`, sin `ACS_CACHE_READ_FAILED`. Sample `1184` + 2 estaciones RSSI (-30, -69) colgadas de ese count.
- GPV slim: solo `TotalAssociations` del radio 1; cursor `acs-gpv:{deviceId}`.
- `POST .../acs/wifi-refresh` 2310/2328 → 409 (`Sin deviceId`); no se encoló GPV. Esos GET 360 en el clon devolvieron 500 (grafo incompleto / sin adaptadores); no tocan ACS.
- Backoffice `vite --mode staging` → `/subscriptions/2329/service-health`: ACS FRESH, gráfica de 2 asociados, RSSI reciente.
- Prod: 0 filas `acs_wifi_count_sample` para este device.

Causa extra de `written=0` tras GPV fresco: `datetime` MySQL sin fracciones + `Timestamp` con millis no encontraba el id post-upsert (`ACS_SAMPLE_ID_NOT_FOUND` tragado como `ACS_CACHE_READ_FAILED`). Fix: truncar a segundos (`HealthSqlTime`) y fallback al último sample del device.

## Cierre

Cierre ejecutado: `./scripts/deploy.sh --env staging` (sin `--with`). Staging HTTP 200, 0 clases `servicehealth`, overlay `servicehealth.enabled=false`. Tag ACS `lab` y `subscription_acs.lab=1` en 2329 **siguen**.
