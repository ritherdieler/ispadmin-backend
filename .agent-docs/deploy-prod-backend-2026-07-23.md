# Deploy producción backend — 2026-07-23

## Resultado

| Campo | Valor |
|-------|-------|
| Estado | OK |
| Rama | `develop` (`origin/develop` al día) |
| Commit | `633c7e1` — *Merge branch 'feature/mapa-digital-wip-stash' into develop* |
| Release | `1.0.3+633c7e1` |
| Host | `212.85.13.47` (Tomcat Docker `tomcat9027`) |
| Comando | `./scripts/deploy.sh --deploy` |
| Observability | Deploy event registrado en `/observability/releases` |

Nota: `main` del repo sigue en el commit inicial (`b397067`) y **no** se usó ni se mergeó. El flujo documentado de prod (`.agent-docs/deploy-flow.md`) despliega el WAR del working tree actual; en la práctica prod sale de `develop`.

## Pasos ejecutados

1. `git fetch origin && git checkout develop && git pull origin develop`
2. Confirmación: `HEAD` = `633c7e1` (local = remoto).
3. Working tree limpio (`RELEASE_DIRTY=0`).
4. `./scripts/deploy.sh --deploy`:
   - Build `mvnw clean package -DskipTests -Ddjl.linux`
   - `verify-djl-war.sh` OK
   - Actualización `APP_RELEASE=1.0.3+633c7e1` en `/opt/gigafiber/.env`
   - Recreate contenedor `tomcat9027`
   - Deploy `target/ispadmin.war` (~246 MB)
   - Espera app + registro de release

## Verificación post-deploy

| Check | Resultado |
|-------|-----------|
| `docker ps` `tomcat9027` | Up |
| `APP_RELEASE` en VPS | `1.0.3+633c7e1` |
| `GET http://127.0.0.1:8080/ispadmin/` (en VPS) | **200** |
| `GET https://api.gigafiberperu.cloud/ispadmin/` | **200** |
| `GET .../smart-map/summary` (sin auth) | **401** (ruta viva; requiere login) |
| Logs DJL | `Motor facial DJL listo para generar descriptores.` |
| Spring Boot | `Started WispAdminApplication` |

El puerto `8080` del VPS está publicado solo en `127.0.0.1`; el acceso externo es vía nginx / `api.gigafiberperu.cloud`.

## Pendientes

- Confirmar `VITE_MAPBOX_ACCESS_TOKEN` (u equivalente) en el **frontend** de producción.
- Smoke autenticado de Smart Map en prod:
  - `GET /ispadmin/smart-map/summary`
  - `GET /ispadmin/smart-map/road-route?...`
  - `GET /ispadmin/smart-map/collection-route?...`
  - Login ADMIN → `/smart-map` carga KPIs y mapa
- Revisar en VPS variables backend de routing OSRM/Mapbox si aplica (`smartmap.routing.*` / secretos Mapbox server-side).

## Referencias

- Flujo operativo: `.agent-docs/deploy-flow.md`
- Piloto mapa: `docs/DEPLOY_PILOTO_MAPA_INTELIGENTE.md`
- Merge mapa + WIP: `.agent-docs/merge-feature-mapa-digital-into-develop-2026-07-23.md`
- Script: `scripts/deploy.sh`
