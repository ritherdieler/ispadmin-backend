# Copiar contexto para LLM en Observabilidad (2026-07-13)

Botón "Copiar contexto para LLM" en el dashboard que, con un clic, copia al portapapeles un
dossier Markdown con el máximo contexto de un error, listo para pegar en un LLM agéntico. El
contenido lo arma el backend agregando todas las fuentes disponibles.

## Endpoints backend

Bajo `/observability/**`, protegidos por `X-Obs-Session` (token ADMIN) vía
`ObservabilityApiKeyFilter`. Devuelven `LlmContextDto(format="markdown", content, generatedAt)`
o `404` si no existe la entidad.

- `GET /observability/issues/{id}/llm-context`
- `GET /observability/traces/{traceId}/llm-context`
- `GET /observability/sessions/{sessionId}/llm-context`

Controlador: `observability/controller/ObservabilityLlmContextController.kt`.
DTO: `LlmContextDto` en `observability/dto/QueryDtos.kt`.

## Servicio agregador `ObsLlmContextService`

`observability/service/ObsLlmContextService.kt`. Inyecta `ObsQueryService`,
`ObsTraceQueryService`, `ObsSessionQueryService`, `ObsDatabaseQueryService`,
`ObsAlertEventRepository` y `ObjectMapper`. Tres orquestadores:

- `buildIssueContext(issueId)`: issue + `getLatestEvent` → si el evento trae `correlationId`
  agrega la traza (`getTrace`), si trae `sessionId` agrega la sesión (`getSession`), consulta
  SQL/N+1 en ventana ±5 min alrededor de `eventTimestamp` y las alertas del issue.
- `buildTraceContext(traceId)`: traza + primer evento correlacionado + sesión + ventana SQL/N+1.
- `buildSessionContext(sessionId)`: sesión + evento con stacktrace (o el primero) + su traza.

Cada `build*` devuelve `null` (→ 404) si la entidad raíz no existe. Las fuentes secundarias
(trace/session/SQL/alertas) se agregan de forma tolerante a fallos (`safe { ... }`), así un
evento sin traza ni sesión no rompe la generación.

### Secciones del dossier Markdown

1. **Preámbulo LLM**: rol (ingeniero senior backend/Android/web), objetivo (causa raíz + fix) y
   formato de respuesta esperado (causa raíz, fix, verificación, riesgos). El sujeto varía según
   el scope (issue / traza / sesión).
2. **Resumen del issue**: título, fingerprint, plataforma, severidad, estado, tipo de error,
   ocurrencias, first/last seen, entorno, release, ticket + URL.
3. **Último evento**: mensaje, tipo, severidad, feature/action, timestamp, trace-id, session-id,
   URL, HTTP método/status, duración, user-agent, entorno, release.
4. **Stacktrace** (prefiere el simbolizado) en bloque de código, truncado a 6000 chars.
5. **Breadcrumbs** cronológicos (máx. 50) como JSON compacto.
6. **Tags / Context / Device / User** como JSON compacto (omite vacíos).
7. **Traza distribuida**: waterfall textual de spans (nombre, kind, duración, self-time, status,
   http, SQL), marcando `[ERROR]`; máx. 120 spans.
8. **SQL / N+1**: top queries y candidatos N+1 en la ventana ±5 min (SQL truncado a 400 chars).
9. **Sesión**: resumen + timeline de eventos (máx. 60) + trazas de la sesión.
10. **Alertas relacionadas** por `issueId` (máx. 15).
11. **Replay**: solo metadata (id, formato, duración, tamaño); sin blob.

Límites de truncado (`STACKTRACE_MAX`, `SQL_MAX`, `MAX_BREADCRUMBS`, `MAX_SPANS`, etc.) mantienen
el dossier pegable.

## Frontend (dashboard `ispadmin-observability-web`)

- Tipo `LlmContextDto` en `src/types/observability.ts`.
- API en `src/lib/observabilityApi.ts`: `getIssueLlmContext(id)`, `getTraceLlmContext(traceId)`,
  `getSessionLlmContext(sessionId)` (GET vía `apiClient`, envía `X-Obs-Session`).
- Hook `src/hooks/useCopyToClipboard.ts`: `navigator.clipboard.writeText` + estado `copied` con
  reset a 1.5 s.
- Componente `src/components/CopyLlmContextButton.tsx`: recibe `fetcher: () => Promise<LlmContextDto>`,
  maneja estados loading/copiado/error con iconos `Copy`/`Check`/`Loader2`/`TriangleAlert`
  (feedback inline; el repo no tiene toasts).
- Montado en el header de `IssueDetailPage.tsx`, `TraceDetailPage.tsx` y `SessionDetailPage.tsx`;
  cada botón solo llama a su endpoint y copia `content` (no depende de datos ya cargados).

## Build y deploy (2026-07-13)

- **Backend**: `./scripts/deploy.sh` (`mvnw clean package -DskipTests -Ddjl.linux` + verify + subida
  del WAR al contenedor `tomcat9027`) → `GET /ispadmin/` **HTTP 200**.
- **Dashboard**: `./scripts/deploy.sh --deploy` (build del SPA + `rsync --delete` de `dist/` a
  `/var/www/gigafiber/observability`); bundle con `CopyLlmContextButton` desplegado →
  `GET https://observability.gigafiberperu.cloud/` **HTTP 200**.
- No se hizo commit/push (queda para el usuario).

## Fuera de alcance

- "Source context" (snippets de código por frame aprovechando `sourcesContent` de los sourcemaps).
- Enviar el contexto directamente a un LLM desde la app (aquí solo se copia al portapapeles).
