# Observabilidad — Timeline por dominio y desglose por feature (2026-07-12)

## Objetivo

Aprovechar las columnas indexadas `feature`/`action` de `obs_event` (ya
pobladas desde `tags` en la ingesta) para mejorar la **reproducción** de errores
y la **agrupación por dominio**, sin romper contratos existentes ni el pipeline
de fingerprint/issues.

## Cambios

### 1. Timeline por sesión filtrable por dominio

`GET /observability/sessions/{sessionId}` acepta dos parámetros opcionales:

- `feature` — filtra los eventos de la sesión por dominio (ej. `payment`).
- `action` — filtra por acción concreta (ej. `register_payment`).

Sin parámetros el comportamiento es idéntico al anterior (todos los eventos
ordenados por `createdAt DESC`). Con filtros, permite reconstruir solo el camino
de un dominio dentro de la sesión previa al error.

### 2. Desglose por dominio en el overview

`GET /observability/stats/overview` incluye el nuevo campo `eventsByFeature`:
un `Map<String, Long>` con el top 10 de features por volumen de eventos en las
últimas 24h. Sirve para identificar rápidamente qué dominio genera más ruido.

## Archivos modificados

| Archivo | Cambio |
|---------|--------|
| `observability/repository/ObsEventRepository.kt` | Nuevos queries `findSessionEventsFiltered(sessionId, feature, action)` y `countGroupedByFeatureSince(from, pageable)` |
| `observability/service/ObsSessionQueryService.kt` | `getSession(sessionId, feature?, action?)` usa el query filtrado cuando hay filtros |
| `observability/controller/ObservabilitySessionController.kt` | `detail` expone `@RequestParam feature`/`action` opcionales |
| `observability/dto/QueryDtos.kt` | `OverviewStatsDto.eventsByFeature: Map<String, Long>` |
| `observability/service/ObsQueryService.kt` | `overview()` puebla `eventsByFeature` con `countGroupedByFeatureSince` |

## Compatibilidad

- Parámetros de query opcionales → el endpoint de timeline mantiene su contrato.
- `eventsByFeature` es un campo nuevo aditivo en el DTO de respuesta.
- No se toca el cálculo de fingerprint ni la agrupación en `obs_issue`.
- Se reutiliza el índice `idx_obs_event_feature`.

## Validación

- `./mvnw -o compile` → **BUILD SUCCESS** (2026-07-12).
