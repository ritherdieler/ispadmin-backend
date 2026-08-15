# Deploy prod — WhatsApp facturas consolidadas en recordatorios — 2026-08-13

| Componente | Release / commit | Notas |
|------------|------------------|--------|
| Backend | `1.0.3+51970b5` | Fast-forward `feature/whatsapp-investigation` → `develop`; `./scripts/deploy.sh --deploy` OK |
| Backoffice | `c71bf0b` | `npm run build -- --mode production` + rsync → `/var/www/gigafiber/backoffice/` |

## Smoke

- `GET /ispadmin/` → HTTP 200
- Observability release event `1.0.3+51970b5` registrado
- https://backoffice.gigafiberperu.cloud/ → HTTP 200
- `assets/WhatsAppSendTab-Bb06fuWS.js` → HTTP 200

## Cambio

Los candidatos y el template de recordatorio de pago consolidan todas las facturas impagas de la suscripción (`invoiceCount`, `periodSummary`, monto total), no solo la más antigua.
