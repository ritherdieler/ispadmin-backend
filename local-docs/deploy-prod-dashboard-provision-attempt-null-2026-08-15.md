# Hotfix prod — dashboard Android 500 por provisionAttemptCount — 2026-08-15

| Componente | Release | Notas |
|------------|---------|--------|
| Backend | `1.0.3+ea4730b` | `./scripts/deploy.sh --deploy` |

## Causa

Hibernate no hidrata `Int` primitivo si `subscription.provision_attempt_count` es NULL.

## Fix

`provisionAttemptCount: Int?` + Flyway `V24`.
