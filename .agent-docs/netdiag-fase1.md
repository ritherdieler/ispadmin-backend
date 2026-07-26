# NetDiag — Fase 1

## Scaffold (este commit)

Módulo hermano `com.dscorp.wispadmin.netdiag`, opt-in con `net.diag.enabled=false`.

### Paquete

```
netdiag/
  config/     NetDiagConfig, NetDiagProperties, NetDiagApiKeyFilter
  controller/ NetDiagController (/api/netdiag/**)
  domain/     entities + repositories (ddl-auto=update, sin Flyway)
  dto/        health, incident list/detail, error
  exception/  NetDiagExceptionHandler (estilo oltgateway)
  port/       NetDiagDeviceDirectoryPort
  service/    NetDiagIncidentQueryService (stubs)
wispadmin/adapter/NetDiagDeviceDirectoryAdapter  # NetworkDevice → MikrotikDeviceRef
```

### Auth

| Capa | Comportamiento |
|------|----------------|
| `PlatformAuthFilter` | Excluye `/api/netdiag/**` |
| `NetDiagApiKeyFilter` (order 25) | Header `X-Netdiag-Key`; `/health` público |

### Properties (`application-dev.properties`)

```properties
net.diag.enabled=${NET_DIAG_ENABLED:false}
net.diag.api-key=${NET_DIAG_API_KEY:dev-netdiag-key}
net.diag.poll.concurrency=4
net.diag.poll.jitter-ms=5000
net.diag.retention.probe-run-days=30
net.diag.alert.cooldown-minutes=15
net.diag.alert.min-duration-seconds=120
net.diag.whatsapp.noc-phone=
net.diag.whatsapp.template-name=noc_alert_v1
```

### Tablas JPA (`net_diag_*`)

| Tabla | Rol |
|-------|-----|
| `net_diag_target` | Target con `device_ref_id` (sin credenciales) |
| `net_diag_probe_run` | Resultado de cada poll |
| `net_diag_incident` | Incidente NOC |
| `net_diag_incident_event` | Timeline append-only |
| `net_diag_alert_decision` | Decisión del evaluador |
| `net_diag_notification_log` | Log de notificaciones |
| `net_diag_audit_log` | Auditoría |

### Superficie API (stubs)

| Método | Path | Auth | Respuesta scaffold |
|--------|------|------|--------------------|
| GET | `/api/netdiag/health` | ninguna | `{ status: UP, module: netdiag }` |
| GET | `/api/netdiag/incidents` | `X-Netdiag-Key` | `[]` |
| GET | `/api/netdiag/incidents/{id}` | `X-Netdiag-Key` | 404 `incident_not_found` |

### Wiring

- `WispAdminApplication`: scan + EntityScan + EnableJpaRepositories para `netdiag`
- OpenAPI group `netdiag` + scheme `NetDiagApiKey`
- `NetDiagDeviceDirectoryPort` no importa `NetworkDevice`; el adapter vive en `wispadmin`

### Tests

- `NetDiagApiKeyFilterTest`
- `NetDiagPropertiesTest`
- `PlatformAuthFilterNetDiagExclusionTest`
- `NetDiagControllerTest`
- `NetDiagDeviceDirectoryAdapterTest`

## Pendiente (siguiente worker)

No incluido en scaffold:

1. **Polls** — `MikrotikPollAdapter` (REST), `NetDiagPollScheduler` (concurrency + jitter), retención `probe_run`
2. **Alertas** — `AlertEvaluator` idempotente, `CorrelationEngine` (supresión padre-hijo), reason codes, `POLL_STALE`
3. **WhatsApp NOC** — `WhatsAppOpsNotifier` reutilizando `WhatsAppService.sendTemplateMessage`
4. **LLM** — `GET .../llm-context` y `.../diagnostic-json`
5. **Ingest** — `POST /api/netdiag/alerts/ingest` (PON_DOWN)
6. **Backoffice** — página NOC en `ispadmin-backoffice`
