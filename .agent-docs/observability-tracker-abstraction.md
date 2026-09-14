# Observabilidad — Abstracción de Issue Tracker y Webhooks entrantes

Abstracción agnóstica de proveedor para la gestión de tickets (creación asistida) y la
sincronización de dos vías mediante webhooks entrantes. Jira es el primer adaptador; la
arquitectura permite añadir otros proveedores (Linear, GitHub Issues, etc.) sin tocar el núcleo.

## Arquitectura (puertos y adaptadores)

- `port/IssueTrackerPort.kt`: contrato neutral del proveedor.
  - `providerId` / `providerLabel` (p. ej. `"jira"` / `"Jira"`).
  - `isConfigured()`, `testConnection(): TrackerTestResult`.
  - `createTicket(CreateTicketRequest): CreateTicketResult`.
  - `parseWebhook(headers, rawBody): IncomingTrackerEvent?`.
- Modelos neutrales (mismo archivo): `CreateTicketRequest` (`summary`, `descriptionText`, `severity`),
  `CreateTicketResult` (`ok`, `issueKey`, `browseUrl`, `error`), `TrackerTestResult`
  (`ok`, `message`, `accountName`), `IncomingTrackerEvent` (`provider`, `type`, `ticketKey`,
  `rawStatus`, `mappedStatus`) y `TrackerEventType` (`TICKET_DELETED`, `TICKET_STATUS_CHANGED`).
- `adapter/jira/JiraIssueTrackerAdapter.kt`: implementación Jira Cloud REST API v3 (Basic auth
  email:token). Construye el ADF de la descripción, mapea prioridad por severidad y parsea el
  webhook. Reemplaza al antiguo `JiraService`.
- `service/IssueTrackerRegistry.kt`: resuelve el adaptador activo (`observability.tracker.provider`)
  y permite buscar por `providerId`.
- `service/ObsTicketApplicationService.kt`: orquesta creación (`createTicketForIssue`) y
  desvinculación (`unlink`) sobre `ObsIssue`. Reemplaza a `ObsJiraApplicationService`. Compone la
  descripción neutral (fingerprint, plataforma, severidad, estado, ocurrencias, fechas, enlace al
  dashboard, mensaje, correlation-id, URL, HTTP, user-agent, stacktrace).
- `service/ObsTrackerWebhookService.kt`: aplica el `IncomingTrackerEvent` sobre la BD.
- `controller/ObservabilityTrackerWebhookController.kt`: endpoint público del webhook.
- `controller/ObservabilityTrackerController.kt`: `GET /observability/tracker/info` (para el dashboard).

## Persistencia neutral (compatibilidad hacia atrás)

`entity/ObsIssue.kt` añade columnas neutrales (Hibernate `ddl-auto=update` las crea):

- `tracker_provider` (VARCHAR 30)
- `tracker_issue_key` (VARCHAR 100, indexada `idx_obs_issue_tracker_key`)
- `tracker_browse_url` (VARCHAR 500)

Se mantiene `jira_issue_key` (deprecada) durante la ventana de transición: al crear un ticket con
proveedor `jira` se rellenan ambos; al desvincular o borrar se limpian ambos.

`service/TrackerBackfillRunner.kt` (`ApplicationRunner`) migra de forma idempotente los issues con
`jira_issue_key` no nulo y `tracker_issue_key` nulo → rellena `tracker_provider=jira`,
`tracker_issue_key` y `tracker_browse_url` (`<baseUrl>/browse/<key>`). Se ejecuta en cada arranque
pero solo actúa sobre pendientes.

## Configuración: namespace neutral + fallback

Namespace nuevo (preferente):

```properties
observability.tracker.provider=jira
observability.tracker.webhook.secret=<secreto-fuerte>
# Opcional: credenciales por proveedor (si no, se hereda de observability.jira.*)
observability.tracker.jira.base-url=...
observability.tracker.jira.email=...
observability.tracker.jira.api-token=...
observability.tracker.jira.project-key=...
observability.tracker.jira.issue-type=Tarea
observability.tracker.jira.dashboard-base-url=...
observability.tracker.jira.priority-by-severity.error=High
# Mapeo de estados del proveedor -> estado interno (OPEN/RESOLVED/IGNORED/MUTED)
observability.tracker.jira.status-mapping[Listo]=RESOLVED
observability.tracker.jira.status-mapping[En curso]=OPEN
observability.tracker.jira.status-mapping[Tareas por hacer]=OPEN
```

`ObservabilityProperties.resolvedJira()` construye la config efectiva: cada campo de
`observability.tracker.jira.*` cae en fallback a `observability.jira.*` (legado). Si se usa el
legado, el adaptador emite un `WARN` de deprecación. Así el entorno dev actual (que solo tiene
`observability.jira.*`) sigue funcionando sin cambios de credenciales.

En producción (`application.properties`):

```properties
observability.tracker.provider=${OBS_TRACKER_PROVIDER:jira}
observability.tracker.webhook.secret=${OBS_TRACKER_WEBHOOK_SECRET:}
```

### Secreto de webhook (dev)

Generado con `openssl rand -hex 32` y configurado en `application-dev.properties`:

```
observability.tracker.webhook.secret=e519973b9420271dbfc4492f82bfd714716a3a5c939454407935554a19428f51
```

En producción NO se versiona: se inyecta por variable de entorno `OBS_TRACKER_WEBHOOK_SECRET`.

## Endpoints

- `POST /observability/issues/{id}/ticket` — crea ticket en el proveedor activo (body opcional
  `{summary, description}`). `POST /observability/issues/{id}/jira` se mantiene como alias legado.
- `DELETE /observability/issues/{id}/ticket` — desvincula el ticket (limpia `tracker_*` y
  `jira_issue_key`). No borra el ticket en el proveedor.
- `GET /observability/tracker/info` — `{provider, label, configured}` (requiere API key).
- `POST /observability/tracker/{provider}/webhook` — webhook entrante (público, protegido por secreto).

## Webhook entrante (seguridad y contrato)

- Ruta pública `POST /observability/tracker/jira/webhook`, **excluida del filtro de API key**
  (`ObservabilityApiKeyFilter.shouldNotFilter` detecta `/observability/tracker/**/webhook`).
- Autenticación por secreto compartido en la cabecera `X-Obs-Tracker-Secret`. Si el secreto está
  vacío o no coincide → `401`.
- El adaptador Jira acepta dos formatos de payload:
  1. Simplificado (recomendado para Jira Automation):
     `{"event":"jira:issue_deleted|jira:issue_updated","key":"KAN-1","status":"Listo"}`
  2. Clásico de webhook Jira:
     `{"webhookEvent":"jira:issue_deleted","issue":{"key":"KAN-1","fields":{"status":{"name":"Listo"}}}}`
- Tipo de evento: contiene `delet` → `TICKET_DELETED`; contiene `updat`/`transition` →
  `TICKET_STATUS_CHANGED`.
- Mapeo de estado: se usa `observability.tracker.jira.status-mapping` (insensible a
  mayúsculas/espacios) y, si no hay coincidencia, un mapeo por defecto (`listo/done/finalizada/...`
  → RESOLVED, `cancelado/cancelled` → IGNORED, `por hacer/en curso/...` → OPEN). Si el estado no
  mapea, el cambio se ignora (no se toca la BD).
- Acción: `TICKET_DELETED` limpia `tracker_*` + `jira_issue_key`; `TICKET_STATUS_CHANGED` actualiza
  `ObsIssue.status`. La búsqueda del issue es por `tracker_issue_key` y, en fallback, por
  `jira_issue_key`.

## Pasos manuales en Jira (Automation) — pendiente para el usuario

La sincronización 2 vías requiere que Jira notifique al backend. Con **Jira Automation**:

1. Jira → Proyecto `KAN` → Project settings → Automation → Create rule.
2. Regla 1 (borrado):
   - Trigger: **Issue deleted**.
   - Acción: **Send web request**.
     - URL: `https://api.gigafiberperu.cloud/ispadmin/observability/tracker/jira/webhook`
     - Method: `POST`, Web request body: **Custom data**.
     - Headers: `X-Obs-Tracker-Secret: <secreto de producción>` y `Content-Type: application/json`.
     - Body:
       ```json
       {"event":"jira:issue_deleted","key":"{{issue.key}}"}
       ```
3. Regla 2 (transición/cambio de estado):
   - Trigger: **Issue transitioned** (o **Issue updated**).
   - Acción: **Send web request** (misma URL, header y método).
     - Body:
       ```json
       {"event":"jira:issue_updated","key":"{{issue.key}}","status":"{{issue.status.name}}"}
       ```
4. Guardar y activar ambas reglas.

Nginx: la ruta ya cae bajo `/ispadmin/` (mismo host del backend), por lo que normalmente NO se
requiere configuración adicional. Verificar que el proxy permita `POST` con cuerpo JSON y que no
filtre la cabecera `X-Obs-Tracker-Secret` (si hay reglas de allowlist de headers, añadirla).

## Verificaciones realizadas

- Backend: `sh mvnw -q -DskipTests compile` → OK.
- Restart devtools (copiar `application-dev.properties` a `target/classes`) → backfill migró 2
  issues (KAN-52/KAN-53 heredados) a `tracker_*`.
- `GET /observability/tracker/info` → `{provider: jira, label: Jira, configured: true}`.
- `POST /observability/jira/test` → `{ok:true, accountName:"Sergio Carrillo Diestra"}`.
- Webhook (curl) contra `/observability/tracker/jira/webhook`:
  - Secreto inválido → `401`.
  - `jira:issue_updated` (KAN-52, "Listo") → `applied:true`; en BD el issue 3 pasó a `RESOLVED`.
  - `jira:issue_deleted` (KAN-53, formato clásico) → `applied:true`; en BD el issue 2 quedó sin
    `tracker_*`/`jira_issue_key`.
- Limpieza: KAN-52 y KAN-53 ya no existían en Jira (404); se limpiaron sus referencias colgantes en BD.
