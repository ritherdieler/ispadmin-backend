# Observabilidad — Replays conscientes del formato (rrweb vs frames)

Fase 0 (Backend) del plan "Session Replay Android por fotogramas". Hace que los
replays distingan su formato para poder coexistir el replay web (`rrweb`) con el
replay Android por fotogramas (`frames`). Antes el formato era implícito y el
nombre de archivo `.rrweb.gz` estaba fijo.

## Cambios

### Entidad `ObsReplay` (`entity/ObsReplay.kt`)
- Nueva columna `format` (`@Column(name = "format", length = 30)`), `var format: String? = "rrweb"`.
- Se crea sola con `ddl-auto=update`; los registros existentes quedan `NULL`
  (se tratan como `rrweb` en el frontend).

### Controller (`controller/ObservabilityReplayController.kt`)
- `POST /observability/replays` acepta `@RequestParam("format", required = false) format: String?`.
- Se pasa `format` al service.
- La respuesta `201` incluye ahora `format` (además de `id`, `sessionId`,
  `sizeBytes`, `contentEncoding`).
- `GET /{id}` sin cambios: sigue devolviendo `application/json` +
  `Content-Encoding: gzip` (sirve igual para el manifiesto JSON de frames).

### Service (`service/ObsReplayService.kt`)
- `store(...)` recibe `format: String?`. Resuelve `resolvedFormat = format ?: "rrweb"`.
- Nombre de archivo parametrizado: `"<uuid>.<resolvedFormat>.gz"`
  (`.rrweb.gz` o `.frames.gz`).
- Persiste `format = resolvedFormat` en la entidad.

### DTOs (`dto/QueryDtos.kt`)
- `ReplaySummaryDto` expone `format: String?`.
- `EventDto` expone `format: String?` (el formato del replay enlazado por `replayId`).
- La extensión `ObsEvent.toDto(rawJson, format: String? = null)` recibe el
  formato como parámetro (la entidad `ObsEvent` no tiene columna `format`; el
  formato viene del replay enlazado).

### Mapeos en servicios de consulta
- `ObsSessionQueryService.getSession`: mapea `format` en cada `ReplaySummaryDto`
  y resuelve el `format` de cada `EventDto` con un mapa `replayId -> format`
  construido a partir de los replays de la sesión.
- `ObsQueryService`: inyecta `ObsReplayRepository`. `getOccurrences` y
  `getLatestEvent` resuelven el `format` del `EventDto` vía `findAllById` /
  `findById` sobre los `replayId` de los eventos.
- `ObsTraceQueryService.getTrace`: inyecta `ObsReplayRepository` y resuelve el
  `format` de los `EventDto` del trace por `replayId`.

## Contrato para el frontend (nombres exactos)
- `ReplaySummaryDto.format: String?`
- `EventDto.format: String?`
- Valores esperados: `"rrweb"` | `"frames"` | `null` (legacy → tratar como `rrweb`).
- Respuesta 201 de `POST /observability/replays` incluye `format`.

## Enlace evento-replay
Sin cambios: `EventIngestRequest.replayId` -> `ObsEvent.replayId` sigue igual.

## Verificación
`./mvnw -q -DskipTests test-compile` → OK.

## Validación E2E (2026-07-11)
Backend arrancado fresco con `./mvnw spring-boot:run` (perfil dev, MySQL local
`ispadmin_dev`, context-path `/ispadmin`, puerto 8080). `ddl-auto=update` creó la
columna: `SHOW COLUMNS FROM obs_replay LIKE 'format'` → `format varchar(30) NULL`.

Con la app Android (flavor dev) en dispositivo físico se generó un replay real:

- `obs_replay` id=3: `format=frames`, `session_id=388bce58-…`, `size_bytes=7641`,
  `duration_ms=5981`, `content_encoding=gzip`,
  `file_path=…/replays/2026/07/11/….frames.gz`.
- `obs_event` id=47 (`crash`, `fatal`): `session_id=388bce58-…` (no nulo),
  `replay_id=3` → enlace evento↔replay correcto.
- `GET /observability/replays/3` (header `X-Obs-Api-Key: dev-obs-dashboard-key`)
  → `200`, `Content-Type: application/json`, `Content-Encoding: gzip`.
  Manifiesto: `format=frames`, `width=400`, `height=866`, `durationMs=5981`,
  13 `frames` (cada uno `{t, img}`, `img` base64 de WebP → cabecera `RIFF…WEBP`).
- `GET /observability/sessions/388bce58-…` → el arreglo `replays[]` incluye el
  replay id=3 con `format=frames`; los `EventDto` de la sesión resuelven
  `format=frames` para el evento enlazado.

Detalle consolidado (incluye enmascarado PII):
[`/Users/sergiocarrillo/gigafiber/.agent-docs/observabilidad-session-replay-android-frames-e2e-2026-07-11.md`].
