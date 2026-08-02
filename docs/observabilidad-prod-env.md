# Variables de observabilidad en producción

El backend en perfil `prod` lee las API keys desde el entorno (archivo típico: `/opt/gigafiber/.env`, inyectado al contenedor Tomcat).

## Obligatorias para ingestión y dashboard

| Variable de entorno | Property Spring | Consumidor |
|---------------------|-----------------|------------|
| `OBS_API_KEY_ANDROID` | `observability.api-keys.android` | APK Android (`X-Obs-Api-Key`) |
| `OBS_API_KEY_DASHBOARD` | `observability.api-keys.dashboard` | Dashboard observability-web |
| `OBS_API_KEY_BACKOFFICE` | `observability.api-keys.web-backoffice` | Backoffice web |
| `OBS_API_KEY_ASISTENCIAS` | `observability.api-keys.web-asistencias` | Asistencias web |

La clave Android del APK **debe ser idéntica** a `OBS_API_KEY_ANDROID` (misma cadena embebida en `OBS_API_KEY` al compilar flavor `prod`).

## Validación en arranque

`ObservabilityApiKeysStartupValidator` registra **ERROR** en log al iniciar con perfil `prod` si alguna de las cuatro claves anteriores está vacía.

## Verificación rápida (servidor)

```bash
grep -E '^OBS_API_KEY_ANDROID=' /opt/gigafiber/.env | awk -F= '{print length($2)}'
```

Longitud > 0 y el contenedor Tomcat debe exponer la variable (`printenv OBS_API_KEY_ANDROID`).

## Verificación ingestión Android

```bash
KEY="$(grep '^OBS_API_KEY_ANDROID=' /opt/gigafiber/.env | cut -d= -f2- | tr -d '\"')"
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$OBS_BASE_URL/observability/events" \
  -H "Content-Type: application/json" \
  -H "X-Obs-Api-Key: $KEY" \
  -H "X-Correlation-Id: probe" \
  -d '{"events":[{"eventType":"log","severity":"info","message":"probe","sessionId":"00000000-0000-0000-0000-000000000001","timestamp":'$(date +%s000)'}]}'
```

Esperado: `202`.

## Listado de sesiones (backend)

`GET /observability/sessions` agrega sesiones desde `obs_event` **y** sesiones con trazas root en `obs_span` (sesiones solo-HTTP Android visibles aunque no haya eventos).
