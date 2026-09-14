# Deploy prod — bandwidth intelligence query perf (2026-08-31)

## Release

| Campo | Valor |
|-------|-------|
| Versión | `1.0.3+8e46ea4` |
| Branch | `develop` |
| Commits | `bec147c` (SQL aggregate) + `8e46ea4` (fixture test) |
| Comando | `./scripts/deploy.sh --deploy` |
| Resultado | HTTP 200 en `/ispadmin/`; deploy event registrado en observability |

## Qué entra

Optimización de `GET /traffic/bandwidth/v1/overview` y `/series` (y rutas relacionadas): agregación SQL por bucket, filtros por router/ids, counts de anomalías. Detalle: [bandwidth-intelligence-query-perf-2026-08-31.md](./bandwidth-intelligence-query-perf-2026-08-31.md).

No incluye el WIP local de service-health / Wi‑Fi telemetry (quedó en working tree tras el deploy).

## Verificación sugerida

1. Backoffice → `/bandwidth-intelligence` pestaña Red, rango 24h: debe cargar sin `timeout of 10000ms exceeded`.
2. Medir latencia autenticada de overview + series (objetivo ≪ 10 s).
