# Construcción: tres ambientes (local / staging / prod) — 2026-08-31

Mismo VPS (`212.85.13.47`), mismo Tomcat (`tomcat9027`), segundo WAR y schema MySQL `ispadmin_staging` en `mysql8033`.

## Qué quedó

- Perfil Spring `staging`: `/ispadmin-staging`, JDBC `ispadmin_staging`, `gigafiber.scheduling.enabled=false`, collectors/WhatsApp/UDP off. Listeners UDP con `@Profile("!staging")`.
- Maven `-Pprod-war` / `-Pstaging-war` hornea `spring.profiles.active` (y context-path en staging).
- `./scripts/deploy.sh --env staging|prod`. Staging no toca el exploded dir de prod ni `APP_RELEASE`.
- Compose: se quitan `SPRING_PROFILES_ACTIVE` y `SPRING_DATASOURCE_URL` (si no, ambos WAR van a prod). Antes del primer corte se sube un `ispadmin.war` horneado para que prod no arranque en `dev,local`.
- Nginx: `location /ispadmin-staging/` y `/ispadmin-staging/ws`.
- Backoffice: `.env.staging` + `npm run build:staging`.
- Runbook: [ambientes-local-staging-prod.md](./ambientes-local-staging-prod.md). Catálogo `VITE_API_BASE_URL` staging en [vps-secrets-management.md](./vps-secrets-management.md).

## Promoción (2026-08-31)

Staging quedó en `https://api.gigafiberperu.cloud/ispadmin-staging/` (HTTP 200). Perfiles activos `prod,staging`. Compose ya no pinnea `SPRING_PROFILES_ACTIVE` ni `SPRING_DATASOURCE_URL`. Prod (`/ispadmin/`) se restauró con WAR horneado `-Pprod-war` del mismo árbol al cortar el env (GPV 1..N). `./scripts/deploy.sh --env prod` exige git limpio para registrar `APP_RELEASE`.


1. `./scripts/deploy.sh --env staging` → smoke `https://api.gigafiberperu.cloud/ispadmin-staging/`
2. `./scripts/deploy.sh --env prod` (árbol git limpio)
