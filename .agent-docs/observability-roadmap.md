# Roadmap del Hub de Observabilidad — hacia una herramienta tipo Dynatrace

Plan de evolución del hub in-house (`com.dscorp.wispadmin.observability`) para convertirlo en una
herramienta de diagnóstico completa comparable a Dynatrace / Sentry / New Relic, manteniendo la
**regla de oro**: todo bajo el paquete `observability`, tablas prefijo `obs_`, config `observability.*`,
sin FKs al dominio, y listo para extraerse a un servicio independiente.

> Documentos base: `observability-hub.md`, `observability-tracing-spans.md`,
> `observabilidad-database-insights-session-view.md`, `observability-tracker-abstraction.md`,
> `observability-replay-format.md`, `observability-web-sdk.md` (en repos frontend).

---

## 1. Estado actual (lo ya construido)

| Área | Estado |
|---|---|
| Ingesta de eventos + fingerprint + agrupación en issues | ✅ |
| Rate limiting, ingesta async, API key por plataforma | ✅ |
| Tracing distribuido (spans, trace/parent, db_statement, muestreo) | ✅ |
| APM de endpoints (p50/p95/p99, throughput, error rate) | ✅ |
| Database insights (top queries, N+1) | ✅ |
| Session Replay (rrweb web + frames Android) | ✅ |
| Issue tracker (Jira + webhook bidireccional) | ✅ |
| Eventos en vivo (STOMP `/topic/observability/events`) | ✅ |
| Retención por tipo de dato (schedulers) | ✅ |
| SDKs: web (backoffice/asistencias) + Android | ✅ |
| **UI / dashboard de diagnóstico** | ❌ falta |
| **Alertas proactivas y notificaciones** | ❌ falta |
| **Simbolicación (source maps / ProGuard)** | ❌ falta |
| RUM Web Vitals (rendimiento percibido) | ✅ |
| **Ingesta de logs correlacionados** | ❌ falta |
| **Service map / topología** | ❌ falta |
| **Métricas de infra/JVM + pool DB** | ❌ falta |
| **Release health / user impact / regresión** | ❌ falta |
| **SLO / error budgets** | ❌ falta |
| **Monitoreo sintético / uptime** | ❌ falta |
| **Root cause asistido por IA** | ❌ falta |

**Diagnóstico central:** hay una excelente tubería de datos (ingesta + almacenamiento + API de
consulta) pero **no hay UI que la consuma** ni **alertas que avisen sin mirar el panel**. Esos dos
puntos son los que hoy impiden que sea una herramienta de diagnóstico usable.

---

## 2. Principios que se mantienen en todo el roadmap

1. **Aislamiento**: nada nuevo referencia tablas del dominio; todo bajo `obs_*` / `observability.*`.
2. **Degradación silenciosa**: si un subsistema (alertas, IA, logs) falla, la ingesta no se ve afectada.
3. **Portabilidad**: cada feature nueva respeta el "camino de extracción" (sección 11 de `observability-hub.md`).
4. **Contrato estable**: los clientes solo cambian URLs/keys, nunca el contrato de evento.
5. **Costo de almacenamiento controlado**: cada dato nuevo trae su política de retención y muestreo.

---

## 3. Roadmap por fases

Numeración continúa la convención existente (fases 1, 2, 3, 6, 7 ya hechas). Prioridad: **P0** crítico,
**P1** alto, **P2** medio, **P3** posterior.

### Fase 8 — Dashboard de diagnóstico (P0) ✅ IMPLEMENTADO

**Objetivo:** UI que consuma los endpoints ya existentes. Sin esto no hay "herramienta".

**Alcance (frontend, app o módulo en backoffice):**
- **Overview en vivo**: KPIs de `/stats/overview`, serie temporal de `/stats/events-timeseries`,
  suscripción STOMP a `/topic/observability/events` para el feed en tiempo real.
- **Issues**: lista con filtros (`platform`, `severity`, `status`, `environment`, `from/to`, `text`),
  detalle → ocurrencias → cambio de estado → crear/desvincular ticket.
- **Trace explorer**: `/traces` con filtros + **waterfall** de spans en `/traces/{traceId}`.
- **APM**: `/metrics/endpoints` (top lentos/errores) + `/metrics/timeseries?route=`.
- **Database**: `/database/queries` y `/database/nplusone`.
- **Session**: reproductor de replay (rrweb player web / frames Android) enlazado desde el evento.
- **Correlación navegable** entre issue ↔ trace ↔ replay ↔ sesión usando `correlationId`/`sessionId`/`replayId`.

**Cambios de backend:** mínimos. Posibles ajustes:
- Endpoint `/observability/stats/overview` ya incluye métricas de traces; validar CORS/observability-web-config.
- Considerar `GET /observability/sessions/{sessionId}` unificado (timeline de eventos+spans+replay).

**Criterios de aceptación:** un dev puede, desde el panel, pasar de una alerta/issue a su trace,
sus queries y su replay sin salir de la herramienta.

**Esfuerzo:** Alto (es el grueso del trabajo pendiente). **Dependencias:** ninguna (API ya existe).

---

### Fase 9 — Motor de alertas + notificaciones (P0) ✅ IMPLEMENTADO

**Objetivo:** avisar sin necesidad de mirar el panel (lo que hoy `ObsLivePublisher` NO hace: solo empuja a WS).

**Modelo de datos nuevo:**
- `obs_alert_rule`: `id`, `name`, `enabled`, `type` (`ERROR_SPIKE` | `NEW_ISSUE` | `ISSUE_REGRESSION` |
  `ENDPOINT_ERROR_RATE` | `ENDPOINT_LATENCY_P95` | `TRACE_ERROR_RATE` | `EVENT_THRESHOLD`),
  `platform?`, `environment?`, `route?`, `severity?`, `threshold`, `comparator` (`GT/GTE/LT`),
  `window_minutes`, `cooldown_minutes`, `channels_json`, `created_at`.
- `obs_alert_channel`: `id`, `type` (`SLACK` | `TELEGRAM` | `EMAIL` | `WEBHOOK`), `name`, `config_json`, `enabled`.
- `obs_alert_event`: histórico de disparos (`rule_id`, `triggered_at`, `value`, `context_json`, `resolved_at`).

**Servicios nuevos:**
- `ObsAlertEvaluator` (`@Scheduled` cada 1 min): evalúa reglas contra ventanas de `obs_event` /
  `obs_endpoint_metric` / `obs_span`. Baseline móvil para `ERROR_SPIKE` (comparar ventana vs media histórica).
- `ObsAlertNotifier` + adaptadores por canal (patrón puerto/adaptador como el tracker):
  `SlackAlertChannel`, `TelegramAlertChannel`, `EmailAlertChannel`, `WebhookAlertChannel`.
- Enganche en `ObsIngestionService`: al crear issue nuevo o reabrir un `RESOLVED` → evaluación inmediata
  (`NEW_ISSUE` / `ISSUE_REGRESSION`) sin esperar al scheduler.

**Endpoints:** CRUD `/observability/alerts/rules`, `/observability/alerts/channels`,
`GET /observability/alerts/history`, `POST /observability/alerts/channels/{id}/test`.

**Config:** `observability.alerts.enabled`, `observability.alerts.eval-interval-ms`, secretos de canal por env.

**Criterios de aceptación:** un pico de errores en prod dispara un mensaje a Slack/Telegram con enlace
directo al issue en el dashboard, respetando cooldown.

**Esfuerzo:** Medio-Alto. **Dependencias:** Fase 8 (para el deep-link), pero el backend es independiente.

---

### Fase 10 — Simbolicación de stacktraces (P1) ✅ IMPLEMENTADO

**Objetivo:** stacktraces legibles en prod (hoy se guardan crudos/minificados/ofuscados).

**Alcance implementado:**
- **Web**: subir source maps por release y bundle (`POST /observability/symbols/sourcemaps` con
  `platform`, `release`, `bundle`, `file`). Decodificador VLQ propio (`SourceMapConsumer`) que resuelve
  cada frame `archivo.js:línea:col` a su posición original.
- **Android**: subir mapping ProGuard/R8 (`POST /observability/symbols/proguard` con `platform`,
  `release`, `file`). Parser/retrace propio (`ProguardMapping`) que desofusca clase, método y línea.
- Tabla `obs_symbol_artifact`: `id`, `platform`, `app_release`, `type`, `bundle`, `file_name`,
  `file_path`, `checksum` (SHA-256), `size_bytes`, `uploaded_at`.
- Simbolicación **lazy y cacheada**: al consultar la última ocurrencia u ocurrencias de un issue,
  se resuelve el stacktrace y se persiste en `obs_event.stacktrace_symbolicated` (una sola vez).
  Los artefactos parseados se cachean en memoria (`ObsSymbolicationService`).
- `EventDto` expone `stacktraceSymbolicated` + `symbolicated`. El dashboard muestra el simbolizado por
  defecto con toggle "Ver original" y una página **Símbolos** para subir/listar/eliminar artefactos.
- Retención de artefactos en `ObsRetentionScheduler` (`observability.retention.symbol-days`, def. 120).

**Nota:** no se recalcula el fingerprint sobre el stacktrace simbolizado (se mantiene el agrupamiento
actual); queda como mejora futura opcional.

**Esfuerzo:** Medio. **Dependencias:** integrar con el build de cada cliente (CI que suba artefactos
tras cada release, usando la API key de la plataforma correspondiente).

---

### Fase 11 — RUM Web Vitals (P1) ✅ IMPLEMENTADO

**Objetivo:** medir rendimiento percibido por el usuario, no solo errores.

**Alcance implementado (SDK web + backend + dashboard):**
- SDK web (ambas copias, backoffice y asistencias): dep `web-vitals` + `webVitals.ts`
  (`initWebVitals`) captura LCP/INP/CLS/FCP/TTFB con `onLCP/onINP/onCLS/onFCP/onTTFB`, encola por
  `page = location.pathname` con `rating`/`navigationType` y hace flush por lotes en
  `visibilitychange`/`pagehide` con `sendBeacon`/`fetch keepalive` a `POST /observability/rum`.
- Backend: entidad/tabla `obs_rum_metric` (agregada por bucket/página/plataforma/métrica, como
  `obs_endpoint_metric`), `ObsRumCollector` (buckets por minuto) + `ObsRumScheduler` (flush 60s),
  `ObservabilityRumController` (`POST /observability/rum`, validación de métricas + rate-limit) y
  `ObsRumQueryService`.
- Endpoints de consulta: `GET /observability/metrics/web-vitals` (agregado por página+métrica con
  p75 y distribución) y `GET /observability/metrics/web-vitals/timeseries`.
- Live STOMP: `publishRumMetric` con discriminador `"type":"OBS_WEB_VITAL"` en
  `/topic/observability/events`.
- Config `observability.rum.*` y retención `observability.retention.rum-metric-days` (7).
- Dashboard: vista de Web Vitals por página con umbrales Good/Needs-Improvement/Poor.

Detalle backend en `observability-fase11-rum.md`; captura web en `observability-web-sdk.md`.

**Esfuerzo:** Medio. **Dependencias:** SDK web (dep `web-vitals`), Fase 8 para visualizar.

---

### Fase 12 — Ingesta de logs correlacionados (P1)

**Objetivo:** tercer pilar de observabilidad (logs) enlazado a `trace_id`/`correlationId`.

**Alcance:**
- `POST /observability/logs` batch: `{ level, message, logger, trace_id, span_id, correlation_id,
  platform, timestamp, attributes_json }`.
- Tabla `obs_log` (particionable por fecha; retención corta, p.ej. 7 días).
- Appender opcional en el backend (Logback) que enrute logs `WARN/ERROR` al hub vía el puerto existente.
- Dashboard: explorador de logs con filtro por `trace_id` (ver logs + spans + evento juntos).

**Esfuerzo:** Medio. **Dependencias:** ninguna dura; sinergia con Fase 8.

---

### Fase 13 — Service map / topología (P2)

**Objetivo:** grafo de dependencias entre servicios/rutas (equivalente a Smartscape).

**Alcance:**
- Job de agregación (`@Scheduled`) que construye aristas caller→callee desde `obs_span`
  (`kind`, `parent_span_id`, `http_route`, `platform`) con latencia y error rate por arista.
- Tabla `obs_topology_edge` (bucket, source, target, calls, errors, p95).
- Endpoint `/observability/topology?from=&to=` → nodos + aristas.
- Dashboard: grafo interactivo con color por salud.

**Esfuerzo:** Medio-Alto. **Dependencias:** volumen suficiente de spans; Fase 8.

---

### Fase 14 — Métricas de infraestructura/JVM (P2)

**Objetivo:** salud del runtime, no solo de endpoints.

**Alcance:**
- Exponer con Micrometer: CPU, memoria heap/non-heap, GC, hilos, **pool de conexiones HikariCP**,
  cache hit rate. Volcar snapshots periódicos a `obs_runtime_metric` (o scrapeo interno).
- Endpoint `/observability/runtime/metrics` + serie temporal.
- Dashboard: panel de host/JVM correlacionable con picos de latencia.

**Esfuerzo:** Bajo-Medio (Micrometer ya suele estar disponible en Spring Boot). **Dependencias:** ninguna.

---

### Fase 15 — Release health, user impact y regresión (P2)

**Objetivo:** entender el impacto real y detectar regresiones tras deploys.

**Alcance:**
- **User impact**: contar usuarios únicos afectados por issue (derivar de `user_json`/`session_id`).
  Añadir `affected_users` a `obs_issue` (mantenido en ingesta) o vista agregada.
- **Release health**: crash-free rate por `release`/`platform`; tabla `obs_release_health`.
- **Regresión**: cuando un issue `RESOLVED` reaparece con un `release` posterior al de resolución →
  marcar como `REGRESSION`, reabrir y disparar alerta (hoy pasa a `OPEN` en silencio, ver
  `ObsIngestionService.persistEvent`). Requiere guardar `resolved_in_release` en `obs_issue`.
- Dashboard: adopción de release + comparativa de salud entre versiones.

**Esfuerzo:** Medio. **Dependencias:** Fase 9 (alertas), Fase 8 (visualización).

---

### Fase 16 — SLO / error budgets (P3)

**Objetivo:** definir objetivos de servicio y consumo de presupuesto de error.

**Alcance:**
- `obs_slo`: `name`, `target` (p.ej. 99.9%), `sli_type` (availability/latency), `route?`, `window_days`.
- Cálculo periódico del budget consumido a partir de `obs_endpoint_metric` / traces.
- Alertas de burn-rate (integra con Fase 9).
- Dashboard: tarjetas de SLO con budget restante.

**Esfuerzo:** Medio. **Dependencias:** Fases 9 y 14.

---

### Fase 17 — Monitoreo sintético / uptime (P3)

**Objetivo:** detectar caídas aunque no haya tráfico real.

**Alcance:**
- `obs_synthetic_check`: `name`, `url`, `method`, `interval`, `expected_status`, `timeout`, `enabled`.
- Scheduler que ejecuta checks y guarda resultados en `obs_synthetic_result`.
- Alertas de caída (Fase 9). Dashboard: uptime % y latencia por check.

**Esfuerzo:** Bajo-Medio. **Dependencias:** Fase 9.

---

### Fase 18 — Root cause asistido por IA (P3)

**Objetivo:** equivalente ligero a "Davis": resumen y causa probable.

**Alcance:**
- Servicio que, a demanda o al crear un issue, arma un prompt con stacktrace (simbolizado),
  breadcrumbs, spans del trace y logs correlacionados, y pide a un LLM un resumen + causa probable + fix sugerido.
- Guardar el resultado en `obs_issue.ai_summary` (cacheado, regenerable).
- Dashboard: botón "Explicar este issue".

**Esfuerzo:** Medio. **Dependencias:** Fases 10 y 12 mejoran mucho la calidad del análisis.

---

## 4. Trabajo transversal (cross-cutting)

Independiente de fases, a abordar en cuanto haya volumen real de datos:

- **Muestreo tail-based** (`observability.tracing.sample-rate` hoy fijo en 1.0): quedarse siempre con
  traces con error o lentos, muestrear el resto. Evita explosión de `obs_span` en MySQL (`LONGTEXT`).
- **Escala de almacenamiento**: particionar por fecha las tablas de alto volumen (`obs_event`, `obs_span`,
  `obs_log`) o evaluar un almacén columnar (ClickHouse) para spans/logs manteniendo issues en MySQL.
- **Seguridad**:
  - Claves por plataforma con scope y **rotación** (hoy comparación plana en `ObservabilityProperties.isValidApiKey`).
  - Replays a **object storage** cifrado (hoy disco local `./data/observability/replays`).
  - Reforzar masking de PII también en backend (hoy depende del cliente).
- **Backfill controlado** al cambiar fingerprint o simbolicación (evitar duplicar issues).

---

## 5. Secuencia recomendada y dependencias

```
P0:  Fase 8 (Dashboard) ──┬─> Fase 9 (Alertas)
                          │
P1:  Fase 10 (Símbolos) ──┼─> Fase 11 (RUM) ─> Fase 12 (Logs)
                          │
P2:  Fase 13 (Topología) ─┼─> Fase 14 (Infra/JVM) ─> Fase 15 (Release health)
                          │
P3:  Fase 16 (SLO) ─> Fase 17 (Sintético) ─> Fase 18 (IA)
```

Prioridad absoluta: **Fase 8 y Fase 9** (convierten la tubería en herramienta usable y proactiva).

---

## 6. Tabla resumen

| Fase | Feature | Prioridad | Esfuerzo | Depende de |
|---|---|---|---|---|
| 8 | Dashboard de diagnóstico ✅ | P0 | Alto | — |
| 9 | Motor de alertas + notificaciones ✅ | P0 | Medio-Alto | 8 (deep-link) |
| 10 | Simbolicación (source maps / ProGuard) ✅ | P1 | Medio | CI clientes |
| 11 | RUM Web Vitals ✅ | P1 | Medio | SDK web, 8 |
| 12 | Logs correlacionados | P1 | Medio | 8 |
| 13 | Service map / topología | P2 | Medio-Alto | spans, 8 |
| 14 | Métricas infra/JVM + pool DB | P2 | Bajo-Medio | — |
| 15 | Release health / user impact / regresión | P2 | Medio | 8, 9 |
| 16 | SLO / error budgets | P3 | Medio | 9, 14 |
| 17 | Monitoreo sintético / uptime | P3 | Bajo-Medio | 9 |
| 18 | Root cause con IA | P3 | Medio | 10, 12 |
| — | Muestreo tail-based / escala / seguridad | transversal | variable | — |

---

## 7. Próximo paso sugerido

Arrancar por la **Fase 8 (Dashboard)**: definir dónde vive (módulo dentro del backoffice vs app
dedicada), maquetar Overview + Issues + Trace explorer contra la API existente, y en paralelo
diseñar el esquema de la **Fase 9 (Alertas)**. Ambas no requieren cambios grandes de backend y
desbloquean el valor de todo lo ya construido.
