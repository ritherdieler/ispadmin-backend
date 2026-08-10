# Deploy prod — WhatsApp ACK suave post-voucher — 2026-08-10

| Componente | Release / commit | Notas |
|------------|------------------|--------|
| Backend | `1.0.3+1c737ed` | `./scripts/deploy.sh --deploy` desde `develop` |

## Cambio

Tras recibir un comprobante, el bot pasa a `AWAITING_RECEIPT_REVIEW` y responde texto libre (`ACK`/`GREETING`/`UNKNOWN`) con `[VOUCHER_PENDING]` en lugar de `[MENU_INVALID]`.

## Smoke

- https://api.gigafiberperu.cloud/ispadmin/ → HTTP 200
- Contenedor: `tomcat9027` recreado y arrancado
- WAR: `ispadmin.war` desplegado (~250 MB)
