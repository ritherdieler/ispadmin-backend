# Deploy prod — WhatsApp investigation (montos + ADMIN quick replies) — 2026-08-14

| Componente | Release / commit | Notas |
|------------|------------------|--------|
| Backend | `1.0.3+9586600` | Fast-forward `feature/whatsapp-investigation` → `develop`; `./scripts/deploy.sh --deploy` OK |
| Backoffice | `e3a8841` | `npm run build -- --mode production` + rsync |

## Smoke

- `GET /ispadmin/` → HTTP 200
- Observability release event `1.0.3+9586600` registrado
- https://backoffice.gigafiberperu.cloud/ → HTTP 200
- `assets/WhatsAppSendTab-BFJ4DPGF.js` → HTTP 200

## Cambio

Total de facturas impagas en recordatorio; quick replies solo ADMIN; bimestrales no se mezclan con cancelados.
