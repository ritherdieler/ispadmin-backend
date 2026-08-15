# Deploy prod — WhatsApp facturas consolidadas en recordatorios — 2026-08-13

| Componente | Release / commit | Notas |
|------------|------------------|--------|
| Backend | `1.0.3+51970b5` | Fast-forward `feature/whatsapp-investigation` → `develop`; `./scripts/deploy.sh --deploy` OK; Tomcat `tomcat9027` |
| Backoffice | `c71bf0b` | `npm run build -- --mode production` + rsync → `/var/www/gigafiber/backoffice/` |

## Smoke

- `GET /ispadmin/` → HTTP 200
- Observability release event `1.0.3+51970b5` registrado
- https://backoffice.gigafiberperu.cloud/ → HTTP 200

## Cambio

`WhatsAppCandidateSql` agrupa facturas impagas por suscripción. El DTO de candidatos y el resolver de plantilla `PAYMENT_REMINDER` usan `UnpaidInvoiceAggregate` (monto total, cantidad y rango de periodos).
