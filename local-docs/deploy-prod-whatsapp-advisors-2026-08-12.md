# Deploy prod — WhatsApp Pidieron asesor — 2026-08-12

| Componente | Release | Notas |
|------------|---------|--------|
| Backend | `1.0.3+7564de3` | `./scripts/deploy.sh --deploy` OK; Tomcat `tomcat9027` |
| Backoffice | `873054c` (rsync) | pill + badge + counts `advisors` |

## Smoke

- `GET /ispadmin/` → HTTP 200
- Observability release event `1.0.3+7564de3` registrado
- `view-counts.advisors` presente en API prod

## Cambio

Vista inbox `ADVISORS` + `hasPendingAdvisorRequest` (espejo de comprobantes para `hablar_asesor`).
