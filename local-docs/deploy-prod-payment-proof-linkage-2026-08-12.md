# Deploy prod — Payment proof linkage — 2026-08-12

| Componente | Release / commit | Notas |
|------------|------------------|--------|
| Backend | `1.0.3+0b0bf3c` | Merge `feature/payment-proof-linkage` → `develop`; `./scripts/deploy.sh --deploy` OK |
| Backoffice | `f585ea3` | Merge feature + fix build TS; `npm run build -- --mode production` + rsync |

## Smoke

- `GET /ispadmin/` → HTTP 200
- Observability release event `1.0.3+0b0bf3c` registrado
- https://backoffice.gigafiberperu.cloud/ → HTTP 200

## Cambio

Vincula imágenes de comprobante de WhatsApp a uno o varios pagos (`PUT /payment`, `PUT /payment/batch`, migración `V20__payment_proof_image_path`).
