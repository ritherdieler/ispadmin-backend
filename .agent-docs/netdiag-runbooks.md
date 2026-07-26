# NetDiag — Runbooks operativos (Fase 1)

## Prerrequisitos

1. `net.diag.enabled=true`
2. `net.diag.api-key` configurado
3. Filas en `net_diag_target` con `device_ref_id` apuntando a `network_device.id` y `enabled=true`
4. REST RouterOS accesible (443 / truststore según `router.os.client.rest.*`)
5. (Opcional WhatsApp) `net.diag.whatsapp.noc-phone` + plantilla Meta `noc_alert_v1` aprobada

## Superficie API (alineada con backoffice NOC)

Base: `/api/netdiag`  
Auth: header `X-Netdiag-Key` (excepto `/health`)

| Método | Path | Response |
|---|---|---|
| GET | `/health` | `{ status, module }` |
| GET | `/incidents?severity&status&targetId&dateFrom&dateTo` | `IncidentSummaryDto[]` |
| GET | `/incidents/{id}` | `IncidentDetailDto` (+ `events[]`) |
| GET | `/incidents/{id}/llm-context` | **text/plain** markdown |
| GET | `/incidents/{id}/diagnostic-json` | JSON mapa en raíz |
| POST | `/incidents/{id}/ack` | `IncidentDetailDto` (`status=ACKNOWLEDGED`) |
| POST | `/incidents/{id}/resolve` | `IncidentDetailDto` (`status=RESOLVED`) |
| POST | `/alerts/ingest` | `{ decisions, openedIncidentIds, suppressed }` |

### Query params lista

| Param | Ejemplo | Notas |
|---|---|---|
| severity | `P0` | case-insensitive |
| status | `OPEN` / `ACKNOWLEDGED` / `RESOLVED` | case-insensitive |
| targetId | `7` | id de `net_diag_target` |
| dateFrom / dateTo | `2026-07-26` o ISO-8601 | filtro sobre `openedAt` |

### Ingest body

```json
{
  "targetId": 2,
  "reasonCode": "PON_DOWN",
  "severity": "P0",
  "title": "PON down gpon 0/1",
  "component": "gpon-0/1",
  "details": "olt=OLT1"
}
```

## DTOs para frontend NOC

### `IncidentSummaryDto`

| Campo | Tipo |
|---|---|
| id | number |
| targetId | number \| null |
| targetName | string \| null |
| dedupKey | string |
| status | `OPEN` \| `ACKNOWLEDGED` \| `RESOLVED` \| … |
| severity | `P0` \| `P1` \| `P2` |
| title | string |
| reasonCode | string \| null |
| openedAt | ISO-8601 |
| lastNotifiedAt | ISO-8601 \| null |

### `IncidentDetailDto`

Summary + `acknowledgedAt`, `resolvedAt`, `events: IncidentEventDto[]`.

### `IncidentEventDto`

`{ id, type, payload, createdAt }` — tipos: `OPENED`, `ALERT_SEEN`, `SUPPRESSED_CHILD`, `WHATSAPP_NOTIFIED`, `ACKNOWLEDGED`, `RESOLVED`, …

## Runbook: incidente LINK_DOWN

1. Abrir detalle en NOC / `GET /incidents/{id}`
2. Copiar LLM context (`GET .../llm-context` → markdown plano)
3. Verificar en MikroTik: `/interface print where name=...`
4. Ack / Resolve desde UI

## Runbook: POLL_STALE

1. Revisar logs del backend (`NetDiag scheduled poll failed`)
2. Verificar `net.diag.enabled`, conectividad REST al router
3. Verificar credenciales vía `network_device` / directory port

## Runbook: WhatsApp no llega

1. `noc-phone` no vacío
2. Incidente con edad ≥ `min-duration-seconds`
3. Sin notificación en ventana `cooldown-minutes`
4. Plantilla `noc_alert_v1` con params `severity`, `title`, `reason_code`, `target`
5. Revisar `net_diag_notification_log.status` / `error`

## Seed mínimo target MK1

```sql
INSERT INTO net_diag_target (name, device_ref_id, enabled, poll_interval_ms, monitor_config, created_at, updated_at)
VALUES (
  'MK1',
  <network_device.id>,
  true,
  60000,
  '{"criticalInterfaces":["ether1"],"expectedFirmware":"7.23.2"}',
  NOW(),
  NOW()
);
```
