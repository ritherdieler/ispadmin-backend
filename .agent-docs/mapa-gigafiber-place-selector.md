# Mapa Gigafiber — selector de place (barrido)

Fecha: 2026-07-22

## Regla de negocio

Elegibles para cobranza en un place:

- Suscripción `ACTIVE`
- Deuda unpaid en periodo `LAST_1_MONTH` / `LAST_2_MONTHS` (o CUSTOM)
- GPS válido **dentro del polígono** `Place.area` del place elegido
- No basta `subscription.place` / `place_id` si el GPS está fuera
- Places **sin** polígono: no se listan; preview/ruta vacíos con mensaje claro

Extiende el barrido (`collection-pending` + `collection-sweep-route` + recalc sweep).  
No cambia `collection-route` de sector.

## Endpoints

### `GET /smart-map/collection-places` (nuevo)

Lista places con `area` ordenados por nombre.

Respuesta: `SmartMapCollectionPlaceDto[]`

| Campo | Tipo |
|---|---|
| `id` | Int |
| `name` | String |
| `latitude` | Double? |
| `longitude` | Double? |
| `areaGeoJson` | String (Polygon GeoJSON) |

### `GET /smart-map/collection-pending`

Query nueva opcional: `place`.

Cache key: `{#debtPeriod,#debtDateFrom,#debtDateTo,#place}`.

Si `place` tiene polígono → filtra con `findSweepEligibleDebtorRowsInsidePlacePolygon`.  
Si `place` sin área → vacío + `periodLabel = "El lugar no tiene polígono configurado"`.  
Sin `place` → lógica previa de barrido global.

DTO incluye `place` echo.

### `GET /smart-map/collection-sweep-route`

Query nueva opcional: `place`.

Misma elegibilidad que preview; orden con Matrix/fallback.

### `POST /smart-map/collection-route/recalculate`

Body ya tenía `place`; con `routeType=sweep` se reenvía a `buildCollectionSweepRoute`.

## Query

`SubscriptionRepository.findSweepEligibleDebtorRowsInsidePlacePolygon(placeName, dateFrom, dateToExclusive)`:

- JOIN place por nombre (case-insensitive) con `area IS NOT NULL`
- `ST_Contains` del GPS del subscription
- Sin exigir match de `subscription.place_id`

## Archivos

- `SubscriptionRepository.kt`
- `PlaceRepository.kt` (`findAllWithPolygonOrderedByName`)
- `SmartMapService.kt` (`placeHasPolygon`, `listCollectionPlaces`, filtro place en preview/sweep)
- `SmartMapController.kt`
- `SmartMapDto.kt` (`SmartMapCollectionPlaceDto`, `place` en pending summary)

## Verificación

```bash
bash ./mvnw -q compile -DskipTests
```

Probar con rol con acceso a deuda:

1. `GET /smart-map/collection-places`
2. `GET /smart-map/collection-pending?debtPeriod=LAST_1_MONTH&place={nombre}`
3. `GET /smart-map/collection-sweep-route?collectorLatitude=...&collectorLongitude=...&place={nombre}&debtPeriod=LAST_1_MONTH`
