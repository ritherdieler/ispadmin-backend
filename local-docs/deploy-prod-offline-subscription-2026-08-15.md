# Deploy prod — registro offline de suscripciones — 2026-08-15

| Componente | Release / commit | Notas |
|------------|------------------|--------|
| Backend | `1.0.3+bce3bc2` | `./scripts/deploy.sh --deploy` OK, Tomcat `tomcat9027` |
| Android | `2.6.4 (56)` prodDebug | App Distribution grupo `gigafiber` |

## Smoke

- `GET /ispadmin/` → HTTP 200
- Observability release event `1.0.3+bce3bc2` registrado

## Cambio

- Idempotencia de alta con `clientRequestId` (Flyway `V22`)
- `IP_CONFLICT` (409 + alerta NOC)
- Soft-fail MikroTik/OLT + reconciliación + `provisioningPending` (Flyway `V23`)
- Flyway: se conservó `V21` `is_bimonthly` ya aplicada en prod (checksum idéntico a `origin/develop`)

## Migraciones nuevas en prod

- `V22__subscription_client_request_id.sql`
- `V23__subscription_provision_status.sql`
