# Deploy piloto Mapa Inteligente

## Ramas

| Repo | Rama piloto | Commit base |
|------|-------------|-------------|
| wispadministrator-main | `feature/Mapa-digital` / `develop` | `85cea62` |
| ispadmin-backoffice-main | `feature/Mapa-digital` | `2b8c89d` |

## Pre-deploy (dev)

1. Ejecutar SQL en BD dev (opcional, complementa fix de codigo):
   - `docs/fix-orphan-assistance-tickets.sql`
2. Verificar backend:
   - `mvnw compile`
   - `GET /ispadmin/smart-map/summary` → 200
   - `GET /ispadmin/assistanceTicket/findAll?status=PENDING` → 200
   - `GET /ispadmin/smart-map/road-route?sector=9%20de%20octubre&originLatitude=-11.235031&originLongitude=-77.380845&destinationLatitude=-11.235380&destinationLongitude=-77.380390` → 200 o 400 validacion
   - `GET /ispadmin/smart-map/collection-route?place=9%20de%20octubre&collectorLatitude=-11.235031&collectorLongitude=-77.380845&includeRoadGeometry=true&userType=ADMIN` → 200 con `roadPath`
3. Verificar frontend:
   - `npm run build`
   - `npm run verify:coverage`
   - `npm run verify:smart-map`
   - `npm run test:map-digital`
   - `npm run verify:road-routing`

## Push a remoto

```powershell
# Backend
cd wispadministrator-main
git push origin feature/Mapa-digital
git checkout develop
git merge feature/Mapa-digital
git push origin develop

# Frontend
cd ispadmin-backoffice-main
git push -u origin feature/Mapa-digital
```

## Variables de entorno (mapa digital)

### Backend (`application-*.properties`)

```properties
smartmap.routing.osrm.base-url=https://router.project-osrm.org
smartmap.routing.osrm.timeout-ms=20000
smartmap.routing.osrm.max-chunk-size=18
smartmap.routing.osrm.max-concurrent=2
```

Documentacion API: `docs/SMART-MAP-ROAD-ROUTE-API.md`.

### Frontend (`.env`)

```env
VITE_MAPBOX_ACCESS_TOKEN=pk....
```

Documentacion: `docs/MAPA-DIGITAL-IMPLEMENTACION.md` (repo frontend).

## Deploy staging/prod

1. Backend: desplegar WAR/JAR desde `develop` con profile `dev` o `prod`.
2. Frontend: publicar `dist/` tras `npm run build` apuntando `baseUrl` al API correcto en `src/services/config.ts` y con `VITE_MAPBOX_ACCESS_TOKEN` configurado.
3. Smoke test post-deploy:
   - Login ADMIN → `/smart-map` carga KPIs y mapa
   - Login SALES → panel prospecto + consulta cobertura
   - Login TECHNICIAN → sin capa de deuda
   - Tickets PENDING no devuelven 500

## Rollback

- Backend: revertir merge en `develop` o redeploy commit anterior.
- Frontend: redeploy build anterior sin ruta `/smart-map`.
