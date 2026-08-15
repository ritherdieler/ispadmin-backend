# Deploy prod — WhatsApp investigation (montos + ADMIN quick replies) — 2026-08-14

| Componente | Release / commit | Notas |
|------------|------------------|--------|
| Backend | `1.0.3+9586600` | Fast-forward `feature/whatsapp-investigation` → `develop`; `./scripts/deploy.sh --deploy` OK; deploy event registrado |
| Backoffice | `e3a8841` | `npm run build -- --mode production` + rsync → `/var/www/gigafiber/backoffice/` |

## Smoke

- `GET /ispadmin/` → HTTP 200
- Observability release event `1.0.3+9586600` registrado
- https://backoffice.gigafiberperu.cloud/ → HTTP 200
- `assets/WhatsAppSendTab-BFJ4DPGF.js` → HTTP 200

## Cambio

- Recordatorio de pago usa el **total de facturas impagas** (no el monto de una sola).
- Mutar respuestas rápidas exige rol **ADMIN**.
- Candidatos `isBimonthly` se quedan en la pestaña bimestral aunque la suscripción esté cancelada.
