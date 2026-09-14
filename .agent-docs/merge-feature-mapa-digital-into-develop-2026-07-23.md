# Merge `feature/Mapa-digital` → `develop` (2026-07-23)

## Resumen

Se completó el merge incompleto de Smart Map / Mapa digital en `develop`, conservando OLT, observability y el resto de cambios de develop.

| Rol | SHA |
|-----|-----|
| `develop` pre-merge | `4589257` |
| Tip `feature/Mapa-digital` | `f68afe9` |
| Merge commit | `b9308cc` |

## Conflictos resueltos (aditivos)

| Archivo | Resolución |
|---------|------------|
| `.gitignore` | Se conservaron entradas de develop (`/data/`, `.cursor/`) y de feature (`/data/observability/replays/`, `/.cursor/`). |
| `pom.xml` | Dependencias develop (`sshd-core`, `springdoc-openapi-ui`) + feature (`spring-boot-starter-cache`, `caffeine`). |
| `GlobalExceptionHandler.kt` | Handlers de observability/upload (develop) + handlers Smart Map / Mapbox (feature). |
| `SubscriptionRepository.kt` | `findCancelledByFiberOnuSn` (OLT/develop) + queries de deudores / Smart Map (feature). |

## Validación

- `./mvnw -q -DskipTests compile` — OK
- `./mvnw -q -Dtest=SectorValidationServiceTest test` — OK

## Stash WIP (aplicado después)

El WIP quedó **pendiente** en el merge inicial a propósito; luego se integró en un follow-up documentado en:

→ [`2026-07-23_smart-map-wip-stash-into-develop.md`](./2026-07-23_smart-map-wip-stash-into-develop.md)

| Rol | SHA |
|-----|-----|
| Commit WIP | `4f77164` |
| Merge WIP → develop | `633c7e1` |
| Stash original (dropeado) | `3ffe188` (`WIP pre-merge ... 20260723`) |

`stash@{1}` (Cursor cloud agent) **no** se tocó.

## Push

- Merge inicial: `git push origin develop` OK (sin force): `4589257..b9308cc`
- Follow-up WIP: `b9308cc..633c7e1` (ver doc de stash)
- Remote: https://github.com/ritherdieler/ispadmin-backend.git
- Rama: https://github.com/ritherdieler/ispadmin-backend/tree/develop

## Próximos pasos

1. Redeploy backend (develop ya incluye merge `b9308cc` + WIP `633c7e1`).
2. Verificar endpoints Smart Map, Matrix/routing Mapbox y validation en el entorno desplegado.
