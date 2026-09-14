# Mapa Gigafiber — Mapbox Directions V1 + Matrix

Fecha: 2026-07-22

## Alcance

Backend de routing para `/smart-map` (reutilizado por Mapa Gigafiber). No hay endpoints de producto nuevos; se enriquecen las respuestas de navegación y el orden de paradas de cobranza/barrido.

## Directions API (`RoadRoutingService`)

Perfil default: `mapbox/driving-traffic` (`smartmap.routing.mapbox.profile`).

### Params en requests con `steps=true` (navegación)

| Param | Valor |
|---|---|
| `voice_instructions` | `true` |
| `banner_instructions` | `true` |
| `voice_units` | `metric` (configurable) |
| `approaches` | `curb` por waypoint (configurable) |
| `waypoint_names` | origen vacío + nombre destino (`destinationName` = nombre/apellido suscripción) |
| `annotations` | `duration,congestion` |
| `overview` | `full` |
| `avoid_maneuver_radius` | solo re-ruta en movimiento (`inMotion=true` o `avoidManeuverRadius`) |

### Endpoint

`GET /smart-map/road-route/navigation`

Query opcionales (ruta pública sin cambios):

- `destinationName` (nombre del destino → `waypoint_names` en Directions)
- `avoidManeuverRadius` (1–1000 m)
- `inMotion` (`true` → usa `smartmap.routing.mapbox.avoid-maneuver-radius-meters`, default 75)

### Contrato DTO `SmartMapNavigationRouteDto` (campos nuevos)

- `voiceInstructions[]`: `distanceAlongGeometry` (**absoluta** desde el inicio de la ruta; convertida desde el valor step-relative de Mapbox), `announcement`
- `bannerInstructions[]`: `distanceAlongGeometry` (misma semántica absoluta), `primaryText`, `secondaryText?`, `type?`, `modifier?`

Conversión en `RoadRoutingService.toAbsoluteDistanceAlongGeometry`:
`absoluta = stepStart + (stepDistance - remainingAlongStep)`.
- `congestion[]`: niveles Mapbox (`low` / `moderate` / `heavy` / `severe` / `unknown`)
- `durationAnnotations[]`: duraciones por segmento de geometría (segundos)

Campos previos sin cambio: `origin`, `destination`, `path`, `distanceMeters`, `durationSeconds`, `routingStatus`, `steps[]`.

## Matrix API (orden de paradas)

Base URL: `https://api.mapbox.com/directions-matrix/v1` (`smartmap.routing.mapbox.matrix-base-url`).

- Perfil: mismo `mapbox/driving-traffic`
- Límite: `matrix-max-coordinates=10` (1 origen + hasta 9 destinos por request; lotes con delay)
- Algoritmo: greedy nearest por `duration` desde posición actual del cobrador
- Uso: `buildCollectionRoute` y `buildCollectionSweepRoute` (también al recalcular)
- Fallback: orden previo (NN + 2-opt en sector; barrido por sectores) si Matrix falla o token ausente

## Propiedades (`application-dev.properties`)

```
smartmap.routing.mapbox.profile=mapbox/driving-traffic
smartmap.routing.mapbox.voice-units=metric
smartmap.routing.mapbox.approaches=curb
smartmap.routing.mapbox.annotations=duration,congestion
smartmap.routing.mapbox.avoid-maneuver-radius-meters=75
smartmap.routing.mapbox.matrix-base-url=https://api.mapbox.com/directions-matrix/v1
smartmap.routing.mapbox.matrix-max-coordinates=10
```

## Archivos tocados

- `SmartMapRoutingProperties.kt`
- `RoadRoutingService.kt`
- `SmartMapRoadRouteService.kt`
- `SmartMapService.kt`
- `SmartMapController.kt` (solo query opcionales en navigation)
- `SmartMapDto.kt`
- `application-dev.properties`

## Consumo backoffice (wire 2026-07-22)

- `smartMapService.getNavigationRoute` reenvía `destinationName`, `avoidManeuverRadius`, `inMotion`.
- `useTurnByTurnNavigation`: voz/banner Mapbox; `destinationName=fullName` en cada leg; `inMotion=true` en re-ruta off-route.
- Detalle UI: `ispadmin-backoffice/.agent-docs/mapa-gigafiber-v1.md`.
- Selector de place (barrido por polígono): ver `mapa-gigafiber-place-selector.md`.
