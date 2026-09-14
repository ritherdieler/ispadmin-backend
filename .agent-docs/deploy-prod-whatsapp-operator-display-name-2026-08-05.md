# Deploy prod: operatorDisplayName en hilo WhatsApp

**Fecha:** 2026-08-05

| Componente | Versión / commit | Método |
|------------|------------------|--------|
| Backend | `1.0.3+83934ad` | `./scripts/deploy.sh --deploy` |
| Backoffice | build local + rsync | `/var/www/gigafiber/backoffice/` |

## Smoke

| URL | HTTP |
|-----|------|
| https://api.gigafiberperu.cloud/ispadmin/ | 200 |
| https://backoffice.gigafiberperu.cloud/ | 200 |

## Nota

Commit previo al deploy: `83934ad` (operatorDisplayName + chat state/inbound pendientes).
