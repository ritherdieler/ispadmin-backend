# Deploy: empaquetado incremental del WAR único

## Selección

`deploy-select-wars.sh` mapea cualquier cambio de módulos Gradle a `core` (un solo artefacto). `--only` se acepta por compatibilidad y también emite `core`.

## Empaquetado

`scripts/deploy-war-needs-rebuild.sh <key> <war-path>`:

| Exit | Significado |
|------|-------------|
| 0 | Empaquetar (falta WAR, sources más nuevos, `FORCE_WAR_REBUILD=1`) |
| 1 | Saltar package (`Skipping WAR package (sources unchanged)`) |

Vigila todos los módulos + `settings.gradle.kts` + `gradle/`. El WAR sale de `./gradlew :core:war` (`core/build/libs/ispadmin.war`) y se copia a `target/$WAR_NAME` para el rsync existente.

## Tests previos

`--deploy` / `--full` usan `./gradlew test`. `--war-only`: no test, no package; exige el WAR en `target/`.

## Forzar rebuild

```bash
FORCE_WAR_REBUILD=1 ./scripts/deploy.sh --env staging
```
