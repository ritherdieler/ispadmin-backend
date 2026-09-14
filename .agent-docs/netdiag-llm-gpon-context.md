# NetDiag — Contexto LLM GPON/PON/OLT

Construcción **2026-08-02**.

## Objetivo

En incidentes con `monitor_config.kind` = `pon` u `olt`, el bundle LLM (`GET /incidents/{id}/llm-context` y `diagnostic-json`) deja de incluir secciones Mikrotik vacías (probe, health, netwatch, optical, traps, impacto RADIUS global) y añade contexto GPON desde **datos ya persistidos** (sin CLI live al copiar).

## Detección de target

`NetDiagMonitorConfigSupport.kind(monitor_config)`:

| kind | Builder GPON | Secciones Mikrotik |
|------|----------------|-------------------|
| `mikrotik` o ausente | no | sí |
| `pon` | sí | no |
| `olt` | sí (sin inventario/abonado PON) | no |

## Secciones markdown (GPON)

Tras **Incidente** y **Timeline**:

1. **Target GPON** — `kind`, `oltId`, `board`/`port` (PON), `mgmtIp` (OLT), target padre (`parentTargetId` → nombre).
2. **Alarmas OLT recientes** — hasta 20 eventos de `net_diag_olt_log_event` por `target_id` (`reasonCode`, `onuIndex`, `severity`, `alarmName`, `isClear`, `rawMessage` truncado a 400 caracteres).
3. **Inventario PON** (solo `kind=pon`) — ONUs `olt_mgr_onu` + `olt_mgr_onu_status_current` filtradas por OLT/board/port; totales online/offline y muestra de offline (máx. 10).
4. **Abonado ONT** (solo `kind=pon` y `onuIndex` resoluble) — índice desde `dedupKey` (`:ont-N`) o título (`ont=N`); lookup ONU → SN → suscripción **ACTIVE** vía `fiberOnu.sn`.

## JSON (`diagnostic-json`)

Claves GPON: `targetContext`, `recentOltLogs`, `ponInventory`, `ontSubscription`.  
No incluye `healthSnapshot`, `radiusImpact`, `latestProbe`, `recentTraps` en targets GPON.

## Componentes

| Clase | Rol |
|-------|-----|
| `NetDiagLlmGponContextBuilder` | Arma `GponLlmContext` |
| `NetDiagLlmContextService` | Rama GPON vs Mikrotik |
| `WispAdminOntSubscriptionAdapter` | `NetDiagOntSubscriptionPort` → `SubscriptionRepository.findActiveByFiberOnuSn` |
| `OltMgrOnuRepository.findByOlt_IdAndBoardAndPortWithStatus` | Inventario PON |

## Tests

```bash
./mvnw test -Dtest='NetDiagLlm*,WispAdminOntSubscriptionAdapterTest'
```

## Webhook P0

Sin cambio: `NetDiagLlmWebhookService` sigue enviando `diagnostic-json`; en PON/OLT el payload ya incluye las claves GPON.
