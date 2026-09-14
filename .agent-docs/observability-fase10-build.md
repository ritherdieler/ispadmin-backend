# Fase 10 — Simbolicación de stacktraces (construcción)

Convierte stacktraces minificados (web) u ofuscados (Android) en su forma legible usando
source maps (v3) y mappings de ProGuard/R8. Todo el trabajo vive en el módulo aislado
`com.dscorp.wispadmin.observability` del backend, más una página en `ispadmin-observability-web`.

## Backend

### Modelo de datos
- `entity/ObsSymbolArtifact.kt` — tabla `obs_symbol_artifact`:
  `id`, `platform`, `app_release`, `type` (`SOURCE_MAP` | `PROGUARD_MAPPING`), `bundle`,
  `file_name`, `file_path`, `checksum` (SHA-256), `size_bytes`, `uploaded_at`.
  Índices por `(platform, app_release, type)`, `bundle` y `uploaded_at`.
- `entity/ObsEvent.kt` — nueva columna `stacktrace_symbolicated` (LONGTEXT) para cachear el
  resultado por evento.
- `repository/ObsSymbolArtifactRepository.kt` — lookups por plataforma/release/tipo/bundle,
  el más reciente por tipo, y `findByUploadedAtBefore` para retención.

### Almacenamiento
- `service/ObsSymbolArtifactService.kt` — guarda el artefacto en disco bajo
  `observability.symbols.storage-dir/yyyy/MM/dd/<uuid>.{map|txt}` (mismo patrón que replays),
  calcula el checksum, y hace upsert: reemplaza el artefacto previo del mismo
  `(platform, release, bundle)` para source maps y del mismo `(platform, release)` para ProGuard.

### Motor de simbolicación
- `service/symbolication/SourceMapConsumer.kt` — decodificador VLQ base64 propio (sin dependencias
  nuevas). Construye por línea generada la lista de segmentos y resuelve `originalPositionFor(línea, col)`
  → `(source, línea, col, name)`.
- `service/symbolication/ProguardMapping.kt` — parser de `mapping.txt` (clases y métodos con rangos
  de línea) + `retrace(clase, método, línea)` que devuelve clase/método/línea originales.
- `service/ObsSymbolicationService.kt` — orquesta según `platform`:
  - `web*` → busca el source map por `bundle` (basename del `.js` en cada frame) y reescribe cada
    frame añadiendo `→ <name> <source>:<línea>:<col>`.
  - `android*` → busca el mapping del release y reescribe cada frame `at Clase.metodo(...)`.
  - Cachea en memoria los artefactos parseados (`ConcurrentHashMap` por `file_path`); `evictCaches()`
    se invoca al subir/eliminar artefactos.

### Integración de lectura (lazy + cache)
- `service/ObsQueryService.kt` — en `getLatestEvent` y `getOccurrences`, si el evento no tiene
  `stacktrace_symbolicated` se simboliza y se persiste una sola vez. El `EventDto` expone
  `stacktraceSymbolicated` y el booleano `symbolicated`.
- No se bloquea la ingesta (`202`): la simbolicación ocurre al consultar, no al ingerir.

### API (bajo el filtro `X-Obs-Api-Key`)
- `POST /observability/symbols/sourcemaps` — params `platform?`, `release`, `bundle`, cuerpo `file`
  (multipart) o binario crudo. `platform` cae al de la API key si no se envía.
- `POST /observability/symbols/proguard` — params `platform?` (def. `android`), `release`, `file`.
- `GET /observability/symbols` — lista artefactos.
- `DELETE /observability/symbols/{id}` — elimina artefacto (fichero + fila).
- Límite `observability.symbols.max-upload-bytes` (def. 60 MB). Para artefactos grandes preferir
  subir el cuerpo como binario crudo (evita el límite de multipart de Spring, 8 MB).

### Configuración (`application*.properties`)
```
observability.symbols.enabled=true
observability.symbols.storage-dir=./data/observability/symbols   # prod: OBS_SYMBOLS_DIR
observability.symbols.max-upload-bytes=60000000                  # prod: OBS_SYMBOLS_MAX_BYTES
observability.retention.symbol-days=120                          # prod: OBS_SYMBOLS_RETENTION_DAYS
```

### Retención
- `scheduled/ObsRetentionScheduler.kt` — borra artefactos (fichero + fila) anteriores a
  `retention.symbol-days` en la purga diaria.

## Frontend (`ispadmin-observability-web`)

- `types/observability.ts` — `EventDto` gana `stacktraceSymbolicated` y `symbolicated`; nuevos tipos
  `SymbolArtifactDto`, `SymbolArtifactType`, `SymbolUploadParams`.
- `lib/observabilityApi.ts` — `listSymbolArtifacts`, `uploadSymbolArtifact(type, params)` (FormData) y
  `deleteSymbolArtifact`. `lib/apiClient.ts` deja que el navegador fije el `Content-Type` multipart
  cuando el cuerpo es `FormData`.
- `features/symbols/queries.ts` — hooks React Query (list/upload/delete con invalidación).
- `features/symbols/SymbolsPage.tsx` — formulario de subida (tipo, plataforma, release, bundle, archivo)
  + tabla de artefactos con borrado. Ruta `/symbols` y entrada de nav "Símbolos" en `AppShell`.
- `features/issues/IssueDetailPage.tsx` — la pestaña Stacktrace muestra el simbolizado por defecto con
  chip "Simbolizado" y toggle "Ver original / Ver simbolizado".

## Flujo de despliegue (CI de cada cliente)
1. Tras compilar un release, subir los artefactos con la API key de la plataforma:
   - Web: por cada bundle, `POST /observability/symbols/sourcemaps` con `release`, `bundle`, `file`.
   - Android: `POST /observability/symbols/proguard` con `release`, `file` (mapping.txt).
2. El `release` enviado debe coincidir exactamente con el `release` que reportan los SDKs en los eventos.

## Verificación
- `./mvnw -q -DskipTests compile` — OK.
- `npm run build` en `ispadmin-observability-web` — OK.
