# NetDiag — Rendimiento del listado de incidentes (fix N+1 + summary)

## Problema

Traza APM `37584c42cbb999d58b8c1d1cca74442d` sobre `GET /api/netdiag/incidents`:

- `NetDiagIncidentQueryService.listIncidents` hacía `findAll()` + filtros en JVM y accedía a `incident.target` (LAZY) por cada fila → **N+1** sobre `net_diag_target`.
- Patrón agregado: ~**1854 calls** al `SELECT` de `net_diag_target` en 74 trazas; el SQL era ~**90%** del tiempo del endpoint.
- Amplificado por el backoffice NOC: polling de lista cada 10 s + sidebar P0 cada 30 s descargando la lista completa solo para contar.

## Cambios Fase 1 — N+1 y fetch explícito

Archivo repo: `src/main/kotlin/com/dscorp/wispadmin/netdiag/domain/repository/NetDiagRepositories.kt`

| Método nuevo | Firma | Uso |
|---|---|---|
| `findForList` | `(statuses: Collection<String>, severity: String?, targetId: Long?, fromAt: Instant?, toAt: Instant?): List<NetDiagIncident>` — JPQL `SELECT DISTINCT i … LEFT JOIN FETCH i.target … ORDER BY i.openedAt DESC` con predicados opcionales | `listIncidents` (1 sola query, sin filtros JVM) |
| `findByIdWithTarget` | `(id: Long): Optional<NetDiagIncident>` — `LEFT JOIN FETCH i.target` | `findIncident` (detalle, ack, resolve, silence) y `NetDiagLlmContextService` (`buildMarkdown` / `buildDiagnosticJson`) |

`listIncidents` y `summarizeIncidents` van anotados con `@Transactional(readOnly = true)`.

Cobertura: `NetDiagIncidentRepositoryJpaTest` (`@DataJpaTest` H2, 5 tests) verifica fetch join y filtros; `NetDiagIncidentQueryServiceTest` mockea las firmas nuevas.

## Cambios Fase 2 — Default de estados activos, índices y summary

### Default del listado

Si `GET /api/netdiag/incidents` llega **sin `status`**, el backend restringe a estados activos: `OPEN`, `ACKNOWLEDGED`, `SILENCED`. Para histórico (`RESOLVED`) hay que pedirlo explícito con `status=RESOLVED` y/o `dateFrom`/`dateTo`. La UI del NOC refleja el default con la opción "Activos".

### Índices JPA (`NetDiagIncident`)

| Índice | Columnas | Motivo |
|---|---|---|
| `idx_net_diag_incident_status_opened_at` | `status, opened_at` | listados NOC por estado + recencia |
| `idx_net_diag_incident_target_status` | `target_id, status` | filtro `targetId` y queries de correlación |

Se generan por `ddl-auto`; validar con `EXPLAIN` en staging si el volumen de `RESOLVED` es alto.

### Endpoint summary

`GET /api/netdiag/incidents/summary` (auth `X-Netdiag-Key`) → `IncidentsSummaryDto`, calculado con counts SQL (`countByStatusIn`, `countBySeverityAndStatusIn`, `countByReasonCodeAndStatusIn`) sobre estados `OPEN`/`ACKNOWLEDGED`:

```json
{ "openCount": 12, "p0OpenCount": 3, "pollStaleCount": 2 }
```

El backoffice lo consume en `netdiagService.getIncidentsSummary` para el badge P0 del sidebar (`useNetDiagOpenP0Count`), sin descargar la lista.

## Cambios Fase 3 — CorrelationEngine

- Bucle de ancestros: se eliminó el doble round-trip `existsByTarget_IdAndStatus` + `findByTarget_IdAndStatus`; ahora un solo `findByTarget_IdAndStatus(...).firstOrNull()`.
- `findOpenByReason` usa `findByTarget_IdAndStatusAndReasonCode(targetId, "OPEN", reasonCode)` en vez de cargar todos los OPEN y filtrar en memoria.
- `existsByTarget_IdAndStatus` se eliminó del repositorio por quedar sin usos.

## Verificación

| Verificación | Estado |
|---|---|
| Tests backend `./mvnw test -Dtest='*NetDiag*,*Correlation*,*AlertEvaluator*'` | ✅ 80 tests en verde (2026-08-01) |
| Tests backoffice `npx vitest run` | ✅ 203 tests en verde (2026-08-01) |
| Traza APM: repetir patrón y confirmar 1 fetch join + p95 ↓ | ⏳ pendiente en entorno real |
| SQL agregado ±5 min: caída de ~1854 calls a `net_diag_target` | ⏳ pendiente en entorno real |
| `EXPLAIN` de los índices nuevos en staging | ⏳ pendiente |
| E2E `ispadmin-backoffice/scripts/e2e-noc-netdiag.mjs` con backend vivo | ⏳ pendiente (compatible por análisis estático: siembra incidentes OPEN y no asserta sobre RESOLVED en el listado default) |

Plan de origen: `netdiag_incidents_perf` (traza `37584c42cbb999d58b8c1d1cca74442d`). Doc backoffice: `ispadmin-backoffice/.agent-docs/netdiag-noc-fase1.md`.
