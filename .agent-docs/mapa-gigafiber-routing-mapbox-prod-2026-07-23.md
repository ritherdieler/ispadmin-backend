# Mapa Gigafiber: rutas dejaron de seguir calles tras merge (2026-07-23)

## Síntoma

Tras merge/deploy de Mapa Digital, la ruta en Mapa Gigafiber se dibuja en línea recta entre paradas (no sigue calles).

## Causa raíz

El merge a `develop` introdujo **routing 100% Mapbox en el backend**:

- `RoadRoutingService` → Mapbox Directions + Matrix
- `SmartMapRoadRouteService.enrichCollectionRoute()` enriquece `collection-sweep-route` / `collection-route` con `roadPath`

Si **no hay token Mapbox en el servidor**, cada tramo cae en fallback:

- `routingStatus`: `fallback` o `partial`
- `fallbackReason`: `mapbox_error` / `straight_line`
- El front pinta `roadPath` recto o usa `path` entre paradas

### Por qué pasó en prod y no en local

| Entorno | Config Mapbox routing |
|---------|------------------------|
| Local (`dev,local`) | `application-dev.properties` + `MAPBOX_ACCESS_TOKEN` al arrancar Maven |
| Prod (`prod`) | **Faltaba** bloque `smartmap.routing.*` en `application-prod.properties` **y** `MAPBOX_ACCESS_TOKEN` en `/opt/gigafiber/.env` |

El front solo tenía `VITE_MAPBOX_ACCESS_TOKEN` (mapa base). **El backend también necesita el token** para calcular rutas por calle.

## Fix aplicado

1. `application-prod.properties`: bloque `smartmap.routing.mapbox.*` (igual que dev)
2. `scripts/deploy.config.example`: documentar `MAPBOX_ACCESS_TOKEN`
3. `scripts/deploy.sh`: `update_release_env` escribe/sincroniza `MAPBOX_ACCESS_TOKEN` si está definido al desplegar; avisa si falta en VPS

## Desplegar corrección en prod

```bash
# En scripts/deploy.config.local (gitignored) o export antes del deploy:
MAPBOX_ACCESS_TOKEN=pk....   # mismo pk. del frontend

./scripts/deploy.sh --deploy
```

Verificar en VPS que `docker exec tomcat9027 printenv MAPBOX_ACCESS_TOKEN` devuelve valor.

Smoke: respuesta de `collection-sweep-route` con `routingStatus: ready` y `roadPath` con muchos puntos (no solo paradas).

## Deploy aplicado (2026-07-23)

| Campo | Valor |
|-------|-------|
| Commit | `bfbcb5e` |
| Release | `1.0.3+bfbcb5e` |
| MAPBOX | Sincronizado a `/opt/gigafiber/.env` desde deploy (mismo pk. que front) |
| Comando | `./scripts/deploy.sh --deploy` |

Tras probar en prod: hard refresh en backoffice; si la ruta sigue recta, inicia cobranza de nuevo (cache local de ruta puede guardar fallback previo).

## Verificación en DevTools

Network → `collection-sweep-route` → Response:

- `routingStatus`: `ready` (ideal) o `partial`
- `roadPath`: array largo (decenas/centenas de `{latitude, longitude}`)
- `routeSegments[].routingStatus`: mayoría `ready`, no `fallback` con `mapbox_error`
