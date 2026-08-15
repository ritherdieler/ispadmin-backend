# Deploy prod — WhatsApp recordatorios bimestrales — 2026-08-14

| Componente | Release / commit | Notas |
|------------|------------------|--------|
| Backend | `1.0.3+524bdce` | Fast-forward `feature/whatsapp-investigation` → `develop`; `./scripts/deploy.sh --deploy` OK; Tomcat `tomcat9027` |
| Backoffice | `bad53f6` | `npm run build -- --mode production` + rsync → `/var/www/gigafiber/backoffice/` |

## Smoke

- `GET /ispadmin/` → HTTP 200
- `APP_RELEASE=1.0.3+524bdce` en el contenedor Tomcat
- https://backoffice.gigafiberperu.cloud/ → HTTP 200
- `assets/WhatsAppSendTab-CpIee-Uy.js` → HTTP 200

## Cambio

Los candidatos de recordatorio de pago exponen `isBimonthly` (`subscription.is_bimonthly`, migración Flyway `V21`). El backoffice agrupa esos deudores en la pestaña **Clientes pago bimestral** y bloquea el envío hasta que haya 2 facturas impagas.

El `UPDATE` de IDs bimestrales en `V21` quedó comentado: la columna existe con default `FALSE` hasta marcar suscripciones a mano.

## Notas

- El registro del deploy event en observabilidad falló (no fatal).
