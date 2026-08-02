# NetDiag — Runbooks operativos (Fase 1 + 1.5)

## Prerrequisitos

1. `net.diag.enabled=true`
2. `net.diag.api-key` configurado
3. Filas en `net_diag_target` con `device_ref_id` apuntando a `network_device.id` y `enabled=true`
4. REST RouterOS accesible (443 / truststore según `router.os.client.rest.*`)
5. (Opcional WhatsApp) `net.diag.whatsapp.noc-phone` (`NET_DIAG_WHATSAPP_NOC_PHONE`, E.164 sin espacios; prod/local: **51982014925**) + plantilla Meta `noc_alert_v1` aprobada
6. (Fase 1.5) Netwatch seed manual, SNMP traps y/o syslog hacia el VPS — ver [netdiag-mikrotik-ros7.md](./netdiag-mikrotik-ros7.md)

## Superficie API (alineada con backoffice NOC)

Base: `/api/netdiag`  
Auth: header `X-Netdiag-Key` (excepto `/health`)

| Método | Path | Response |
|---|---|---|
| GET | `/health` | `{ status, module }` |
| GET | `/incidents?severity&status&targetId&dateFrom&dateTo` | `IncidentSummaryDto[]` — sin `status` devuelve solo activos (`OPEN`, `ACKNOWLEDGED`, `SILENCED`) |
| GET | `/incidents/summary` | `IncidentsSummaryDto` `{ openCount, p0OpenCount, pollStaleCount }` |
| GET | `/incidents/{id}` | `IncidentDetailDto` (+ `events[]`) |
| GET | `/incidents/{id}/llm-context` | **text/plain** markdown |
| GET | `/incidents/{id}/diagnostic-json` | JSON mapa en raíz |
| POST | `/incidents/{id}/ack` | `IncidentDetailDto` (`status=ACKNOWLEDGED`) |
| POST | `/incidents/{id}/resolve` | `IncidentDetailDto` (`status=RESOLVED`) |
| POST | `/alerts/ingest` | `{ decisions, openedIncidentIds, suppressed }` |
| POST | `/traps/ingest` | `TrapIngestResponseDto` |
| POST | `/syslog/ingest` | `SyslogIngestResponseDto` |

### Query params lista

| Param | Ejemplo | Notas |
|---|---|---|
| severity | `P0` | case-insensitive |
| status | `OPEN` / `ACKNOWLEDGED` / `RESOLVED` | case-insensitive; omitido → default activos (`OPEN`, `ACKNOWLEDGED`, `SILENCED`) |
| targetId | `7` | id de `net_diag_target` |
| dateFrom / dateTo | `2026-07-26` o ISO-8601 | filtro sobre `openedAt` |

### Ingest alert body

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

### Trap ingest body

```json
{
  "targetId": 7,
  "trapType": "interfaces",
  "specificType": "linkDown",
  "sourceHost": "38.224.231.2",
  "oid": "1.3.6.1.6.3.1.1.5.3",
  "component": "ether1",
  "varBinds": "{\"ifName\":\"ether1\",\"ifOperStatus\":\"down\"}"
}
```

### Syslog ingest body

```json
{
  "targetId": 7,
  "message": "bridge loop-protect: interface ether5 disabled on bridge1"
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
4. Si hay `UPSTREAM_PROBE_FAIL` OPEN, priorizar causa aguas arriba (internet) antes de tocar enlaces locales
5. Ack / Resolve desde UI

## Runbook: UPSTREAM_PROBE_FAIL

1. Confirmar Netwatch: `/tool netwatch print`
2. Distinguir: router alcanzable vía REST pero probes HTTP/DNS down → problema de transit/WAN, no del core local
3. Revisar WAN `sfp-sfpplus1` / BGP-peer del ISP
4. Alertas de link/óptica hijas pueden estar suprimidas por correlación

## Runbook: OPTICAL_RX_LOW / OPTICAL_TX_FAULT

1. `/interface ethernet monitor <iface> once`
2. Verificar conector/patch, limpieza, SFP correcto
3. Comparar umbrales `net.diag.optical.*`
4. Si TX ausente con módulo presente → posible fallo de láser/SFP

## Runbook: SNMP_TRAP_*

1. Revisar `net_diag_trap_event` / timeline del incidente
2. `SNMP_TRAP_REBOOT` → cruzar con `UNEXPECTED_REBOOT` / uptime
3. `SNMP_TRAP_TEMP` → health + ventilación
4. Confirmar que el trap-target del router apunta al VPS correcto

## Runbook: LOOP_PROTECT_TRIGGERED

1. Identificar bridge/interfaz en el mensaje syslog
2. Buscar loop L2 (CRS / ether mal puenteado)
3. No reactivar puerto hasta aislar el loop

## Runbook: PPP_MASS_DISCONNECT

1. Umbral: `net.diag.syslog.ppp-mass-threshold` en ventana `ppp-mass-window-seconds`
2. Correlacionar con `UPSTREAM_PROBE_FAIL` o caída de OLT/PON
3. Revisar logs PPP y radius si aplica

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

Listado completo de interfaces MK1 (`/interface` en `38.224.231.2`, 2026-07-27):  
`ether1`–`ether8`, `sfp-sfpplus1`, `sfp-sfpplus2`, `LAN`, `SERVICIO CORP.TARAZONA`, `eoip-tunnel1`, `lo`, `vlan1`.  
(Túnel VPS→OLT en **MK2** `wg-ispadmin-vps`; no incluir `gre-ispadmin-vps` en MK1 tras migración WG.)

## Seed target MK2 (WireGuard VPS)

Interfaces críticas MK2 (`38.224.231.4`, 2026-08-01): uplinks `sfp-sfpplus1`–`3`, `ether1`, `ether3`, bridges `LAN` / `LAN_MK1`, WAN VLAN, **`wg-ispadmin-vps`**, `lo`.

Script idempotente: [`scripts/sql/netdiag-target-mk2-wg-seed.sql`](../scripts/sql/netdiag-target-mk2-wg-seed.sql)

```bash
mysql -u root -p ispadmin < scripts/sql/netdiag-target-mk2-wg-seed.sql
```

> **Nota:** varios `ether1`–`ether8` pueden reportar `running=false` en MK1; con todas en `criticalInterfaces` el poll abrirá incidentes `LINK_DOWN` por cada puerto caído. Ajusta la lista si algunos puertos deben ignorarse (`disabled=yes` en RouterOS o quitar del array).

Netwatch/SNMP/syslog en el router: aplicar a mano [netdiag-mikrotik-seed.rsc](./netdiag-mikrotik-seed.rsc).
