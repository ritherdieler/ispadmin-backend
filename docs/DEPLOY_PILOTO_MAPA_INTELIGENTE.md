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
3. Verificar frontend:
   - `npm run build`
   - `npm run verify:coverage`
   - `npm run verify:smart-map`

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

## Deploy staging/prod

1. Backend: desplegar WAR/JAR desde `develop` con profile `dev` o `prod`.
2. Frontend: publicar `dist/` tras `npm run build` apuntando `baseUrl` al API correcto en `src/services/config.ts`.
3. Smoke test post-deploy:
   - Login ADMIN → `/smart-map` carga KPIs y mapa
   - Login SALES → panel prospecto + consulta cobertura
   - Login TECHNICIAN → sin capa de deuda
   - Tickets PENDING no devuelven 500

## Rollback

- Backend: revertir merge en `develop` o redeploy commit anterior.
- Frontend: redeploy build anterior sin ruta `/smart-map`.
