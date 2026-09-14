# Fix índices OBS (TEXT → VARCHAR)

## Problema

Hibernate declara `route` / `page` / `bundle` con `length = 300` e índices, pero en MySQL las columnas quedaron como `longtext`. MySQL rechaza:

`BLOB/TEXT column '…' used in key specification without a key length`

## Dev (`ispadmin_dev`) — aplicado 2026-07-16

Columnas convertidas a `VARCHAR(300)` e índices creados:

- `idx_obs_metric_route`, `idx_obs_metric_bucket_route`
- `idx_obs_rum_page`, `idx_obs_rum_bucket_page_platform_metric`
- `idx_obs_symbol_bundle`

## Prod (`ispadmin` en VPS) — aplicado 2026-07-16

Mismo cambio verificado en MySQL del VPS (`mysql8033`): columnas `varchar(300)` e índices presentes.

Script: [`scripts/sql/fix-obs-text-indexes.sql`](../scripts/sql/fix-obs-text-indexes.sql)

No afecta Meilisearch (solo tablas de observabilidad).
