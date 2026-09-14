# Polling UI y jobs server — IspManager

Complementa la sección de cadencias de [../smartolt-reverse-engineering.md](../smartolt-reverse-engineering.md). Aquí se separa **browser poll** vs **worker cloud**.

## Principio

SmartOLT **no** usa el browser como motor de sync de las ~758 ONUs. El browser:

1. Lee DB cloud (listados, statuses cache, signals cache).
2. Dispara lecturas on-demand a OLT (Get status ~10–15 s, LIVE 60 s).
3. Encola tasks de escritura (authorize, resync, batch…).

IspManager debe replicar esa separación.

## A) Poll UI — View ONU

Endpoint UI: `GET /api/onu/get_onu_status_and_signal/{webId}` (+ `?signal=db`).

| Constante JS | Valor | Rol |
|--------------|------:|-----|
| `STATUS_FAST_WINDOW_MS` | 30000 | Ventana rápida post-load / refresh manual |
| `STATUS_FAST_POLL_MS` | 5000 | Intervalo en ventana rápida |
| `STATUS_RELAXED_POLL_MS` | 15000 | Online post-ventana |
| `ONU_ONLINE_POLL_MS` | 30000 | Flat online (otra rama) |
| `ONLINE_MAX_POLLS` | 10 | Tope ~5 min online |
| `GetOnuStatusIterationsCountdown` | 30 | Cap global polls/página |
| `ONU_OFFLINE_GRACE_SECONDS` | 180 | Offline reciente |
| `ONU_TRANSITION_REFRESH_MS` | 10000 | Offline &lt; 3 min |
| `ONU_OFFLINE_REFRESH_MS` | 30000 | Offline estable |

Reglas: primera señal Online → live OLT; siguientes → `signal=db`. Tab oculto → pausa (`SmartOLTPolling` + `visibilityState`). LIVE! = 60 s.

Evidencia XHR real: ver RE v3 (`get_onu_status_and_signal` ~0.3 s cache; `status` ~12 s CLI).

## B) Poll UI — Configured / Unconfigured (solo tasks)

| Constante | Valor | Cuándo |
|-----------|------:|--------|
| `ACTIVE_REFRESH_MS` | 10000 | ≥1 batch/auto task running |
| `IDLE_REFRESH_MS` | 300000 | Sin tasks (5 min) |

- Configured: `/onu_batch_actions/get_active_tasks`
- Unconfigured: `/onu/get_active_tasks_on_unconfigured`
- Inventario ONU: **no** se re-polla cada N s; se recarga con filtros / `get_configured_list`.

## C) Poll UI — Graphs / Events / Diagnostics

| Área | Cadencia observada | Notas |
|------|--------------------|-------|
| Graphs página | N/A re-captura browser 2026-07-17 | API graphs = PNG under demand |
| Events | N/A intervalo exacto | Severidades en DB model |
| Diagnostics | On-demand | Tests TR069/OLT |

## D) Jobs server inferidos (no en JS)

| Job | Intervalo sugerido (inferido) | Fuente evidencia |
|-----|-------------------------------|------------------|
| `status_poll` | 1–5 min / OLT | `get_onus_statuses` &lt;3 s, 758 filas |
| `signal_poll` | 15–30 min (Postman recomienda) | docs API + cache signals |
| `traffic_poll` | 1–5 min (SNMP preferible) | series/gráficos PNG |
| `autofind_scan` | 30–60 s si Unconfigured activo / Auto actions; si no ~5 min | UI fetch al load + auto tasks |
| `mismatch_scan` | daily + manual | UI “Daily Auto Configuration Check” |
| `olt_health_poll` | ~1 min | `get_olts_uptime_and_env_temperature` (limit header 70) |
| `rollup` | diario | retención RRD-like |

**No observable:** intervalo exacto del worker cloud→OLT, cola interna de tasks, dead-OLT guard server-side.

## E) Rate limits API (headers observados)

| Endpoint / clase | `X-RateLimit-Limit` visto | Notas Postman/UI |
|------------------|--------------------------:|------------------|
| General (statuses, details page, catalogs) | 1000 | 1000/h, 10/s; burst &gt;15/s |
| `get_onu_signal` | 500 | más restrictivo |
| `get_onu_full_status_info` | 300 | heavy CLI |
| `get_olts_uptime_and_env_temperature` | 70 | health-ish |
| `get_all_onus_gps_coordinates` | 5 | muy bajo |
| `get_all_onus_details` sin page | (bloquea 15/h) | usar page + `updated_since` |
| Graphs PNG | a veces sin header | Content-Type image/png |
| Heavy OLT detail (docs) | 30/10 min / OLT | cards/pon/uplink |

429 → `Retry-After` (segundos).

## F) Implicaciones IspManager MVP

1. Endpoints livianos DB: statuses / signals separados de details.
2. Details paginado + `updated_since`; status/signal **no** actualizan `updated_at` de config.
3. UI detail: cadencia View ONU; **prohibido** poll OLT a 5 s para todo el inventario.
4. Toda escritura → `task` + `audit_log`.
5. Pausar polls con tab oculto.
