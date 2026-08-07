# Deploy prod — merge perf WhatsApp + feature hilo (2026-08-07)

| Campo | Valor |
|-------|--------|
| Versión | `1.0.3+e3b247a` |
| Rama | `develop` |
| Tests | `./mvnw clean test` → 902 OK |
| Comando | `./scripts/deploy.sh --deploy` |
| Tomcat | `tomcat9027` |
| Smoke | `GET /ispadmin/` → HTTP 200 |

## Incluye

- Rama `perf/whatsapp-fases-1-2-3-5-6-7` (índices Flyway **V15**, Hibernate/Hikari, N+1 analytics/bandeja/candidatos, inbound pipeline async, envío masivo async `whatsapp.backoffice.async-batch-send=true`, Payment.subscription LAZY).
- Feature hilo (reacciones/edición, **V14** columnas) ya en develop.
- Merge manual: webhook usa `scheduleInboundProcessing` + `processInboundReaction`; migración perf renombrada a V15 por colisión con V14 reacciones.

## Post-deploy

- Flyway aplicará `V15__whatsapp_performance_indexes.sql` en el arranque (si no existían índices equivalentes).
