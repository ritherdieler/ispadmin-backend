# E2E local — OLT Gateway + NetDiag (MK1) + NOC backoffice

Verificación end-to-end de datos reales desde la OLT Huawei (SSH) y el Mikrotik MK1 (REST), más el flujo NOC en backoffice.

Última ejecución verificada: **2026-07-27** — OLT 769 ONUs, 715 con Rx; MK1 probe SUCCESS ~365 ms; NOC E2E OK.

## Prerrequisitos de red

| Recurso | Requisito |
|---------|-----------|
| OLT `10.11.104.2` | Ruta vía MK2/WiFi (`192.168.88.1` → `10.11.104.2`) |
| MK1 `38.224.231.2:443` | HTTPS REST accesible desde la máquina de dev |
| MySQL | `ispadmin_dev` en `127.0.0.1:3306` |

Credenciales dev: `application-dev.properties` + `application-local.properties` (OLT SSH, datasource). MK1 live tests: env `ROUTEROS_MK1_USER` / `ROUTEROS_MK1_PASSWORD` (mismo usuario que `network_device` id=1).

## 1. Levantar servicios

### Backend

```bash
cd ispadmin-backend
./run-dev.sh
```

Comprobar:

```bash
curl -s http://localhost:8080/ispadmin/api/olt-gateway/health
curl -s http://localhost:8080/ispadmin/api/netdiag/health
```

Esperado: `oltReachable: true`, netdiag `status: UP`.

### Backoffice (solo E2E NOC UI)

```bash
cd ispadmin-backoffice
npm run dev
```

Puerto **3000** (`vite.config.ts`). API apunta a `VITE_API_BASE_URL=http://localhost:8080/ispadmin` (`.env.development`).

Opcional en `.env`: `VITE_NETDIAG_API_KEY=dev-netdiag-key` (alineado con `net.diag.api-key` del backend).

Meilisearch (`:7700`) **no** es necesario para NetDiag/NOC E2E.

## 2. Verificación OLT (API + BD)

Header obligatorio (excepto `/health`): `X-Olt-Gateway-Key: dev-olt-gateway-key`

```bash
curl -s -H "X-Olt-Gateway-Key: dev-olt-gateway-key" \
  http://localhost:8080/ispadmin/api/olt-gateway/olt/info

curl -s -H "X-Olt-Gateway-Key: dev-olt-gateway-key" \
  "http://localhost:8080/ispadmin/api/olt-gateway/onus/configured?page=0&size=2"

curl -s -H "X-Olt-Gateway-Key: dev-olt-gateway-key" \
  http://localhost:8080/ispadmin/api/olt-gateway/admin/sync/status
```

Criterios de éxito:

| Check | Esperado |
|-------|----------|
| Health | `UP`, `oltReachable: true`, latencia &lt; 5 s |
| OLT info | `MA5608T`, boards ≥ 1 |
| ONUs configuradas | `totalElements` ≥ 700, items con `onuRxDbm` / `onuTxDbm` |
| Sync inventario | Primer sync: `inserted` ≈ 769; siguientes: `unchanged` ≈ 769, `skippedReason` null |
| Signal poll | `onusUpdated` ≥ 600, `skippedReason` null |
| BD | `olt_mgr_onu` ≥ 700 filas; `olt_mgr_onu_status_current.onu_rx_dbm` ≥ 600 no null |

## 3. Verificación MK1 (NetDiag + tests live)

### Probe en BD

```sql
SELECT pr.id, pr.status, pr.latency_ms, pr.started_at
FROM net_diag_probe_run pr
JOIN net_diag_target t ON t.id = pr.target_id
WHERE t.name = 'MK1'
ORDER BY pr.id DESC LIMIT 1;
```

Esperado: `SUCCESS`, `latency_ms` &lt; 30 000, payload JSON con `interfaces` (≥ 10), `resource.version` (ej. `7.23.2 (stable)`).

### Tests Maven live MK1

```bash
cd ispadmin-backend
export ROUTEROS_MK1_USER=gigafiber2023
export ROUTEROS_MK1_PASSWORD='…'
./mvnw test -Plive-mk1 \
  -Dtest=NetDiagPollServiceLiveIntegrationTest,MikrotikPollAdapterLiveTest
```

Esperado: **3 tests, 0 failures**, `BUILD SUCCESS`.

Clases:

- `NetDiagPollServiceLiveIntegrationTest` — poll Spring → `net_diag_probe_run`
- `MikrotikPollAdapterLiveTest` — REST directo + wiring `netDiagMikrotikClient`

## 4. E2E NOC backoffice (API + Playwright UI)

```bash
cd ispadmin-backoffice
E2E_BASE_URL=http://localhost:3000 \
E2E_API_URL=http://localhost:8080/ispadmin \
E2E_NETDIAG_API_KEY=dev-netdiag-key \
npm run e2e:noc-netdiag
```

Artefactos: `.e2e-artifacts/e2e-noc-netdiag-report.json`, `e2e-noc-netdiag.png`.

Nota: la UI inyecta usuario admin en `localStorage` sin JWT; pueden aparecer **401 en WebSocket** (`/ispadmin/ws`) en consola — no bloquean el flujo NOC.

## 5. Reporte consolidado (opcional)

Tras ejecutar todo, el JSON de resumen puede guardarse en:

`ispadmin-backend/.e2e-artifacts/olt-mk1-noc-e2e-report.json`

## Troubleshooting

| Síntoma | Causa probable |
|---------|----------------|
| `oltReachable: false` / `olt_unreachable` | Sin ruta a `10.11.104.2` o circuit breaker activo |
| SSH `Permission denied` OLT | `olt.gateway.password` vacío; usar `GigaOlt2026` en dev/local |
| MK1 probe `FAILED` timeout | MK1 REST no accesible o truststore SSL |
| API OLT 401 | Header incorrecto; usar `X-Olt-Gateway-Key`, no `X-Olt-Gateway-Api-Key` |
| Sync `inserted: 0` | Normal si inventario ya sincronizado (`unchanged` alto) |
| E2E UI `ERR_CONNECTION_REFUSED :3000` | Levantar `npm run dev` en backoffice |

## Referencias

- Reachability OLT: `.agent-docs/olt-gateway-reachability-unavailable.md`
- NOC backoffice Fase 1: `ispadmin-backoffice/.agent-docs/netdiag-noc-fase1.md`
