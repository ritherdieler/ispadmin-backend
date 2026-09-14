# Deploy prod backend — 2026-09-01

| Campo | Valor |
|-------|--------|
| Rama | `develop` |
| Commit | `41ecc90` — `fix(e2e): arreglar cleanup Firebase en Mac y documentar el runbook de staging` |
| Release | `1.0.3+41ecc90` |
| Comando | `./scripts/deploy.sh --env prod` |
| Suite Maven | verde (compuerta del script) |
| WAR | `/usr/local/tomcat/webapps/ispadmin.war` (241M, 16:14 America/Lima) |
| `APP_RELEASE` | `1.0.3+41ecc90` en `/opt/gigafiber/.env` y env Tomcat |
| Smoke | `GET https://api.gigafiberperu.cloud/ispadmin/` → HTTP 200 |

Incluye el checkpoint `569e88e` (SmartOLT en `OnuService`, VLAN 100 + pool staging `stg`, service-health/traffic) más scripts/runbook e2e.

Backoffice del mismo día: `ispadmin-backoffice/.agent-docs/deploy-prod-2026-09-01.md`.

E2E FIBER prod (Espresso + ping MK2 + hard cleanup): [e2e-prod-fiber-2026-09-01.md](./e2e-prod-fiber-2026-09-01.md) — sub **2334**, TR-069 `COMPLETE`, ping 4/4 a `192.168.30.238`.
