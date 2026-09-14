# Deploy prod — WhatsApp Comprobantes post-resolve — 2026-08-10

| Componente | Release / commit | Notas |
|------------|------------------|--------|
| Backend | `1.0.3+835c378` | `./scripts/deploy.sh --deploy` OK; Tomcat `tomcat9027` |
| Backoffice | `2f94ccd` | `npm run build -- --mode production` + rsync → `/var/www/gigafiber/backoffice/` |

## Cambio

Pestaña **Comprobantes**: solo image/PDF con `created_at` posterior al último `resolvedAt` (`hasPendingReceipt`). Conserva `resolvedAt` al reabrir.

## Smoke

- https://api.gigafiberperu.cloud/ispadmin/ → HTTP 200
- https://backoffice.gigafiberperu.cloud/ → HTTP 200
- Rama: `fix/whatsapp-comprobantes-post-resolve` (backend + backoffice)
