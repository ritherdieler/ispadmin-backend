# NetDiag Fase 1 — Polls, alertas, WhatsApp NOC y LLM

## Objetivo

Módulo `netdiag` operativo (opt-in) con:

- Poll REST exclusivo vía `RouterOs7RestAdapter` (`netDiagMikrotikClient`)
- Motor de alertas idempotente + correlación padre-hijo
- Ingest OLT (`PON_DOWN`)
- Notificaciones WhatsApp NOC reutilizando `WhatsAppService.sendTemplateMessage`
- Bundles LLM (markdown text/plain + diagnostic JSON)
- API alineada con backoffice NOC (`feature/netdiag-noc`)

## Flag y auth

| Property | Default | Notas |
|---|---|---|
| `net.diag.enabled` | `false` | Opt-in; sin esto no hay beans ni scheduler |
| `net.diag.api-key` | (vacío / env) | Header `X-Netdiag-Key` en `/api/netdiag/**` salvo `/health` |

## Contrato UI (backoffice)

| Método | Path | Notas |
|---|---|---|
| GET | `/api/netdiag/incidents` | Query: `severity`, `status`, `targetId`, `dateFrom`, `dateTo`. Sin `status` → solo activos (`OPEN`, `ACKNOWLEDGED`, `SILENCED`) |
| GET | `/api/netdiag/incidents/summary` | Counts SQL: `{ openCount, p0OpenCount, pollStaleCount }` |
| GET | `/api/netdiag/incidents/{id}` | Detail + timeline |
| GET | `/api/netdiag/incidents/{id}/llm-context` | **text/plain** markdown (no JSON wrapper) |
| GET | `/api/netdiag/incidents/{id}/diagnostic-json` | Mapa JSON en raíz |
| POST | `/api/netdiag/incidents/{id}/ack` | → `ACKNOWLEDGED` |
| POST | `/api/netdiag/incidents/{id}/resolve` | → `RESOLVED` |
| POST | `/api/netdiag/alerts/ingest` | OLT / push externo |

Summary incluye `targetName` y `lastNotifiedAt`.

Detalle de rendimiento del listado (fix N+1, índices, summary): [netdiag-incidents-performance.md](./netdiag-incidents-performance.md).

## Poll REST

`MikrotikPollAdapter` obtiene credenciales solo por `NetDiagDeviceDirectoryPort` (`deviceRefId` → `NetworkDevice`). Port REST (`router.os.client.rest.port`).

Lecturas por poll:

| Path REST | Uso |
|---|---|
| `/interface` | LINK_DOWN (críticas), GRE_TUNNEL_DOWN |
| `/system/health` | PSU_FAIL, FAN_FAIL, LOW_VOLTAGE |
| `/system/routerboard` | FIRMWARE_DRIFT |
| `/system/resource` | UNEXPECTED_REBOOT, CPU_HIGH |

Persistencia: `net_diag_probe_run` con payload JSON del snapshot.

Config por target (`net_diag_target.monitor_config` JSON):

```json
{
  "criticalInterfaces": [
    "ether1", "ether2", "ether3", "ether4", "ether5", "ether6", "ether7", "ether8",
    "sfp-sfpplus1", "sfp-sfpplus2", "LAN", "SERVICIO CORP.TARAZONA",
    "eoip-tunnel1", "gre-ispadmin-vps", "lo", "vlan1"
  ],
  "expectedFirmware": "7.23.2",
  "netwatchNames": ["upstream-http", "upstream-dns"],
  "opticalInterfaces": ["sfp-sfpplus1", "sfp-sfpplus2"]
}
```

Seed SQL idempotente MK1: `scripts/sql/netdiag-target-mk1-seed.sql`

## Scheduler y retención

- `NetDiagPollScheduler.scheduledPoll` — `fixedDelay` `net.diag.poll.interval-ms`, jitter `0..jitter-ms`, pool `concurrency`
- Retención diaria 03:30 — borra `probe_run` más antiguos que `net.diag.retention.probe-run-days`

## Alertas

Reason codes Fase 1: `LINK_DOWN`, `GRE_TUNNEL_DOWN`, `PSU_FAIL`, `FAN_FAIL`, `LOW_VOLTAGE`, `FIRMWARE_DRIFT`, `UNEXPECTED_REBOOT`, `CPU_HIGH`, `POLL_STALE`, `DEVICE_UNREACHABLE`, `AUTH_FAILURE`, `TIMEOUT`, `COMMAND_ERROR`, `PON_DOWN` (ingest).

Idempotencia: lock por `targetId` + lookup `dedupKey`/`OPEN` + race → CONTINUE.

Supresión padre-hijo (`CorrelationEngine`): ancestro OPEN → evento `SUPPRESSED_CHILD`.

## WhatsApp NOC

- Plantilla: `net.diag.whatsapp.template-name` (`noc_alert_v1`)
- Destino: `net.diag.whatsapp.noc-phone`
- Params Meta: `severity`, `title`, `reason_code`, `target`
- Anti-spam: `min-duration-seconds` + `cooldown-minutes`

## Properties

```properties
net.diag.poll.concurrency=4
net.diag.poll.jitter-ms=5000
net.diag.poll.interval-ms=60000
net.diag.poll.initial-delay-ms=15000
net.diag.retention.probe-run-days=30
net.diag.alert.cooldown-minutes=15
net.diag.alert.min-duration-seconds=120
net.diag.alert.cpu-threshold=85
net.diag.alert.low-voltage=20.0
net.diag.alert.stale-multiplier=3
net.diag.alert.parent-max-depth=5
net.diag.whatsapp.noc-phone=
net.diag.whatsapp.template-name=noc_alert_v1
net.diag.whatsapp.language-code=es
```

## Tests

```bash
./mvnw test -Dtest='*NetDiag*,*AlertEvaluator*,*AlertSignal*,*WhatsAppOps*,*MikrotikPoll*,*RouterOsUptime*'
```

Fase 1.5 (netwatch/traps/syslog/optical): ver [netdiag-fase1.5.md](./netdiag-fase1.5.md) y [netdiag-mikrotik-ros7.md](./netdiag-mikrotik-ros7.md).  
R4–R5 (wispadmin REST) sigue fuera de alcance.
