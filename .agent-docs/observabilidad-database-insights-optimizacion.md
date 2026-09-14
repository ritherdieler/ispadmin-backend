# Optimización Database Insights / N+1 (2026-07-21)

## Problema

Las consultas de Database Insights (`findNPlusOneCandidates`, agregados por ruta) usaban un
cross join implícito JPQL (`FROM ObsSpan s, ObsSpan root`) sobre `obs_span` (~400k filas).
Con el dashboard en polling cada 30s y rangos de 7d/30d, se acumulaban queries de 10+ minutos
que agotaban el pool Hikari y provocaban HTTP 500 en la API ISP.

## Cambios

### Backend (`ispadmin-backend`)

1. **SQL nativo con INNER JOIN + LIMIT** en:
   - `ObsSpanRepository.findNPlusOneCandidates`
   - `ObsSpanRepository.aggregateDbStatementsByRoute`
   - `ObsSpanRepository.aggregateDbTimeByRoute`
2. **Índices compuestos** en `ObsSpan`:
   - `idx_obs_span_kind_start` `(kind, start_epoch_ms)`
   - `idx_obs_span_trace_parent` `(trace_id, parent_span_id)`
   - `idx_obs_span_root_route_start` `(parent_span_id, http_route, start_epoch_ms)`
3. **Guardrails**:
   - `ObsQueryWindowValidator`: N+1 sin route máx 24h; con route / queries máx 7d → 400
   - `ObsAnalyticsConcurrencyLimiter`: máx 2 consultas analíticas concurrentes → 429
   - `@QueryHints` timeout 30s; fallo → lista vacía + log WARN
   - Parámetro `limit` en `GET /observability/database/nplusone` (default 100, máx 500)
4. Properties (`observability.database.*`):
   - `max-range-hours=168`
   - `nplusone-max-range-hours=24`
   - `nplusone-max-range-hours-with-route=168`
   - `max-concurrent-queries=2`
   - `query-timeout-ms=30000`
   - `nplusone-default-limit=100`

### Frontend (`ispadmin-observability-web`)

1. `lib/dbQueryWindow.ts`: clamp de ventana N+1 a 24h; polling condicional
2. `useNPlusOne`: refetch cada 120s (o desactivado si rango >24h) + clamp cliente
3. `useDbQueries` / `useEndpointDetail`: polling reducido / condicional
4. Banner en DatabasePage cuando se acota N+1; manejo de errores 400/429
5. FixValidationPanel: N+1 solo con `route` seleccionado (evita 2 llamadas globales)

## Checklist post-deploy

```bash
# Índices
docker exec mysql8033 mysql -uroot -p'...' ispadmin -e "SHOW INDEX FROM obs_span WHERE Key_name LIKE 'idx_obs_span_%';"

# Health
curl -s https://api.gigafiberperu.cloud/ispadmin/actuator/health

# Processlist: sin queries obs_span >30s
docker exec mysql8033 mysql -uroot -p'...' -e "SHOW FULL PROCESSLIST;"
```

## Verificación 2026-07-21 (producción)

- Release: `1.0.3+e2e3d98`
- Health: `UP`
- Load average tras deploy: ~0.9 (antes ~10)
- Índices creados: `kind_start`, `trace_parent`; `root_route_start` con prefijo
  `http_route(191)` porque en prod la columna es `longtext` (no VARCHAR).
- Query N+1 nativa 24h: **~18s** (antes 600s+). Cumple el objetivo de no saturar
  Hikari; el criterio ideal <5s queda como mejora futura (p. ej. normalizar
  `db_statement` o materializar agregados).
- Frontend observability-web desplegado con clamp/polling.

## Rollback

Revertir el WAR del backend. Los índices extra son inocuos y pueden permanecer.
