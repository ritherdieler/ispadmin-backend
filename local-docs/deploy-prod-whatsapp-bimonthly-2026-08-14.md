# Deploy prod — WhatsApp recordatorios bimestrales — 2026-08-14

| Componente | Release / commit | Notas |
|------------|------------------|--------|
| Backend | `1.0.3+524bdce` | Fast-forward `feature/whatsapp-investigation` → `develop`; `./scripts/deploy.sh --deploy` OK |
| Backoffice | `bad53f6` | `npm run build -- --mode production` + rsync → `/var/www/gigafiber/backoffice/` |

## Smoke

- `GET /ispadmin/` → HTTP 200
- `APP_RELEASE=1.0.3+524bdce`
- https://backoffice.gigafiberperu.cloud/ → HTTP 200
- `assets/WhatsAppSendTab-CpIee-Uy.js` → HTTP 200

## Cambio

Candidatos de recordatorio con `isBimonthly` (Flyway `V21`). UI: pestaña **Clientes pago bimestral**; envío bloqueado con menos de 2 facturas impagas.
