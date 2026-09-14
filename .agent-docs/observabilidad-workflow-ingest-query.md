# Observabilidad — Workflow tags en ObsEvent

**Fecha**: 2026-07-14

## Columnas (promovidas desde tags Android)

| Tag JSON | Columna | Índice |
|----------|---------|--------|
| `workflowId` | `workflow_id` | `idx_obs_event_workflow_id` |
| `workflowName` | `workflow_name` | — |
| `workflowCategory` | `workflow_category` | — |
| `workflowStatus` | `workflow_status` | — |

## Ingest

`ObsIngestionService.persistEvent` promociona con `tagString` (igual que `feature`/`action`).

Eventos informacionales (`workflow_start`, `workflow_end`, `log` + severity info/debug sin errorType/stack) se persisten **sin** crear/actualizar `ObsIssue`.

## Query

- `GET /observability/sessions/{id}` → `workflows: SessionWorkflowSummaryDto[]` + eventos con campos workflow
- Query param opcional `workflowId`
- `GET /observability/workflows` → listado agregado
- LLM context incluye línea Workflow
