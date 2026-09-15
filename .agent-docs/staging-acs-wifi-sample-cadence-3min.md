# Staging — cadencia ACS Wi‑Fi 3 min (2026-09-03)

> **Superseded parcialmente.** `service.health.acs-gpv-cooldown-seconds` se retiró
> junto con el GPV de lectura. La cadencia ya no es una propiedad de Core sino
> `PeriodicInformInterval` en `gigafiber-bootstrap.js` (1800 s + jitter); en staging
> se baja ese intervalo, no un cooldown. `acs-wifi-sample-target-seconds` sigue
> siendo el umbral de frescura del 360. Ver [wifi-on-inform-flujo-acs-gateway-core.md](./wifi-on-inform-flujo-acs-gateway-core.md).

## Decisión

En **staging** el watcher ACS pide GPV de Wi‑Fi (conteos + estaciones) con objetivo de **3 minutos**, no los 30 min de prod.

| Propiedad | Staging | Prod (default) |
|-----------|---------|----------------|
| `service.health.acs-wifi-sample-target-seconds` | **180** | 1800 (`SERVICE_HEALTH_ACS_WIFI_SAMPLE_TARGET_SECONDS`) |
| `service.health.acs-gpv-cooldown-seconds` | **180** | 1800 (`SERVICE_HEALTH_ACS_GPV_COOLDOWN_SECONDS`) |
| Freshness UI (`wifiSampleFreshSeconds`) | **360** (2× target) | 3600 |

Fuente en repo: `src/main/resources/application-staging.properties` (valores literales `180`).

## Por qué

GenieACS solo actualiza `TotalAssociations` / `AssociatedDevice` en sesión TR‑069. Con target 30 min, 360 puede mostrar 0 estaciones aunque el cliente ya esté en el SSID. Staging/lab necesita lecturas más frecuentes sin pulsar «Actualizar Wi‑Fi».

El poller sigue leyendo cache cada ~2 min (`acs-interval-ms`); el GPV a la ONU se encola cuando la última muestra supera el target y el cooldown lo permite (`AcsWifiRefreshPlanner`).

## Operación

- **No** definir `SERVICE_HEALTH_ACS_WIFI_SAMPLE_TARGET_SECONDS` ni `SERVICE_HEALTH_ACS_GPV_COOLDOWN_SECONDS` en `/opt/gigafiber/.env`: el contenedor Tomcat es compartido y esas env ganarían al `application-staging.properties`.
- Prod sigue en 30 min vía `application.properties` / env opcional.
- Tras desplegar o hot-patch del properties staging, recargar el contexto `ispadmin-staging`.

## Verificación

```bash
docker exec tomcat9027 bash -lc \
  'grep -E "acs-wifi-sample-target|acs-gpv-cooldown" \
   /usr/local/tomcat/webapps/ispadmin-staging/WEB-INF/classes/application-staging.properties'
```

Esperado: ambas líneas `=180`.
