# Smart Map — API de rutas por calles

## Resumen

El backend expone endpoints para calcular rutas de cobranza optimizadas (TSP) y obtener **polylines por calles** vía OSRM, con validación estricta de sector usando `ST_Contains` (PostGIS).

## Endpoints

### `GET /ispadmin/smart-map/road-route`

Ruta origen → destino dentro de un sector.

| Param | Tipo | Descripción |
|-------|------|-------------|
| `sector` | string | Nombre del sector |
| `originLatitude` / `originLongitude` | double | Punto de inicio |
| `destinationLatitude` / `destinationLongitude` | double | Punto de destino |

**200 — `SmartMapRoadRouteDto`**

```json
{
  "sectorName": "9 de octubre",
  "origin": { "latitude": -11.235, "longitude": -77.381 },
  "destination": { "latitude": -11.236, "longitude": -77.382 },
  "path": [{ "latitude": -11.235, "longitude": -77.381 }],
  "distanceMeters": 1234.5,
  "durationSeconds": 180.2,
  "routingStatus": "ready",
  "geometryGeoJson": "{\"type\":\"LineString\",\"coordinates\":[...]}"
}
```

**400 — `SmartMapValidationErrorDto`**

| code | Descripción |
|------|-------------|
| `ORIGIN_OUTSIDE_SECTOR` | Origen fuera del polígono (**solo `/road-route`**) |
| `DESTINATION_OUTSIDE_SECTOR` | Destino fuera del polígono (**solo `/road-route`**) |
| `SECTOR_NO_POLYGON` | Sector sin área definida |
| `SECTOR_NOT_FOUND` | Sector inexistente |
| `INVALID_COORDINATES` | Coordenadas inválidas |

### `GET /ispadmin/smart-map/collection-route`

Parámetros existentes + `includeRoadGeometry=true` para incluir `roadPath`, `roadDistanceMeters`, `routingStatus`.

**Nota:** Las coordenadas del cobrador (`collectorLatitude` / `collectorLongitude`) **no** se validan contra el polígono del sector. El sector define qué deudores se incluyen en la ruta. La validación PIP del cobrador aplica únicamente a `/road-route`.

## Arquitectura

```
SmartMapController (suspend)
  → SmartMapRoadRouteService
      → SectorValidationService (PIP / ST_Contains)
      → RoadRoutingService (OSRM, withContext Dispatchers.IO)
  → SmartMapService (TSP / deudores)
```

## Configuración

`application-dev.properties`:

```properties
smartmap.routing.osrm.base-url=https://router.project-osrm.org
smartmap.routing.osrm.timeout-ms=20000
smartmap.routing.osrm.max-chunk-size=18
smartmap.routing.osrm.max-concurrent=2
```

## Ejemplo curl

```bash
curl "http://localhost:8080/ispadmin/smart-map/road-route?sector=9%20de%20octubre&originLatitude=-11.235031&originLongitude=-77.380845&destinationLatitude=-11.235380&destinationLongitude=-77.380390"

curl "http://localhost:8080/ispadmin/smart-map/collection-route?place=9%20de%20octubre&collectorLatitude=-11.235031&collectorLongitude=-77.380845&includeRoadGeometry=true&userType=ADMIN"
```

## Verificación

```bash
cd ispadmin-backoffice-main
npm run verify:road-routing
```

## Changelog

- **2026-07-13**: `/collection-route` ya no exige que el cobrador esté dentro del polígono del sector.
- **2026-07-13**: Endpoint `/road-route`, OSRM async con corrutinas, validación PIP, `includeRoadGeometry` en collection-route.
