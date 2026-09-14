# Integración WIP stash Smart Map → develop (2026-07-23)

## Contexto

Tras el merge `feature/Mapa-digital` → `develop` (`b9308cc`), quedaba pendiente un stash WIP de Smart Map que **debía** incluirse también en `develop`.

## Stash aplicado

| Campo | Valor |
|-------|--------|
| Identificador al aplicar | `stash@{0}` |
| Mensaje | `WIP pre-merge feature/Mapa-digital into develop 20260723` |
| SHA del stash commit | `3ffe188cbd61fcee450f3e9d9929e61d26e4cfcf` |
| Base del stash | `f68afe9` (`feature/Mapa-digital` tip pre-merge-resolution) |
| Diff aproximado | ~910 líneas (+/-) en 14 archivos tracked + 3 untracked |
| Método | `git stash apply` (no `pop`) |
| Drop posterior | Sí, tras commit + merge + push exitosos (`git stash drop stash@{0}`) |

### Archivos untracked del stash

- `src/main/kotlin/.../smartmap/SmartMapAccessPolicy.kt`
- `src/main/kotlin/.../smartmap/SmartMapIntelligenceService.kt`
- `src/test/kotlin/.../RoadRoutingServiceNavigationGeometryTest.kt`

## Estrategia

Opción A: rama follow-up desde `develop` actual.

1. `git fetch --all --prune`
2. `git checkout develop && git pull`
3. `git checkout -b feature/mapa-digital-wip-stash`
4. `git stash apply stash@{0}`
5. Resolver conflictos
6. Commit WIP
7. Merge `--no-ff` a `develop`
8. `git push origin develop` y `feature/mapa-digital-wip-stash`
9. Drop del stash

## Conflictos

Único conflicto de contenido:

- `src/main/kotlin/com/dscorp/wispadmin/wispadmin/logging/GlobalExceptionHandler.kt`

Resolución (unión):

- **WIP**: handlers Smart Map (`IllegalArgumentException`, `ResponseStatusException`, `handleMethodArgumentNotValid`, detalle enriquecido de `MapboxDirectionsException`).
- **develop**: observability (`ObservabilityReporter`, `reportToObservability`) y `MaxUploadSizeExceededException` / `@Value maxFileSize`.

Resto de archivos: auto-merge limpio (`pom.xml`, Smart Map controllers/services, repos, `application-dev.properties`).

## Commits / SHAs

| Rol | SHA | Mensaje |
|-----|-----|---------|
| Merge previo (ya en develop) | `b9308cc` | Merge branch 'feature/Mapa-digital' into develop |
| Commit WIP | `4f77164` | feat(smart-map): integrar WIP stash (Matrix, voice/banner, validation) |
| Merge WIP → develop | `633c7e1` | Merge branch 'feature/mapa-digital-wip-stash' into develop |
| Tip develop push | `633c7e1` | `origin/develop` actualizado `b9308cc..633c7e1` |

## Contenido principal del WIP

- Mapbox Matrix + routing properties (voice/units, approaches, annotations, avoid-maneuver, matrix max coords).
- Refactors de `SmartMapController` / `SmartMapIntelligenceController`.
- `SmartMapAccessPolicy` + `SmartMapIntelligenceService` (paquete `smartmap`).
- Validación (`spring-boot-starter-validation`) y handlers asociados.
- Ampliaciones en `RoadRoutingService`, `SmartMapService`, repos Place/Subscription.
- Test `RoadRoutingServiceNavigationGeometryTest`.

## Validación

- `./mvnw -q -DskipTests compile` → OK
- `./mvnw -q -Dtest=SectorValidationServiceTest,RoadRoutingServiceNavigationGeometryTest test` → OK

## Push

- `origin/develop` → OK (sin force)
- `origin/feature/mapa-digital-wip-stash` → OK (rama nueva)

## Redeploy

Sí: hace falta **redeploy del backend** en el ambiente que apunte a `develop` para que Matrix/voice/validation/intelligence del WIP queden activos en runtime.
