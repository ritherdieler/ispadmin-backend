# Hotfix prod — dashboard Android 500 por provisionAttemptCount — 2026-08-15

| Componente | Release | Notas |
|------------|---------|--------|
| Backend | `1.0.3+019d0fb` | `./scripts/deploy.sh --deploy` OK |

## Causa

`GET /dashboard` hidrata `Subscription` (instalaciones del mes + `SubscriptionLog.subscription`). Tras V23, `provisionAttemptCount` era `Int` primitivo y Hibernate lanzaba:

`JpaSystemException: Null value was assigned to a property [...provisionAttemptCount] of primitive type`

Android mapeaba el HTTP 500 a `Exception("Error")` → issue obs `Fallo al cargar datos del dashboard`.

## Fix

- `provisionAttemptCount: Int?`
- `V24` backfill `NULL → 0` y `NOT NULL DEFAULT 0`

## Verificación

- `GET /ispadmin/dashboard` HTTP 200
- App 2.6.4 carga el dashboard sin el issue 30/31 nuevo
