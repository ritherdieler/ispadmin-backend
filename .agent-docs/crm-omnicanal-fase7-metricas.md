# CRM Omnicanal WhatsApp — Fase 7: Métricas operativas (Backend)

Fecha: 2026-08-02

## Endpoints (`SECRETARY` | `ADMIN` vía `CrmAccessPolicy`)

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/crm/metrics/summary?from&to` | KPIs operativos + desgloses + referencia CSAT |
| GET | `/crm/metrics/agents?from&to` | Carga por agente (ID hasta catálogo de usuarios) |
| GET | `/crm/metrics/shift-handoff` | Resumen últimas 8 h + alertas de espera |

## Fuentes de datos (sin migración nueva)

- `crm_conversation` — cola, cierres, tiempos claim→resolve
- `crm_assignment_event` — reaperturas, transferencias, resoluciones por agente
- `whatsapp_message_log` — primera respuesta humana (`OPERATOR_*` + `operatorUsername`)
- `whatsapp_audit_log` (`BOT_TRACE`) — motivos de contacto / escalaciones bot→humano
- `ticket_conversation_link` + `AssistanceTicket` — tickets desde chat y SLA vinculado
- `CsatSurveyService.buildSummary` — solo bloque `csatReference` (no duplica lógica Fase 6)

## Definiciones

| Métrica | Cálculo |
|---------|---------|
| Primera respuesta | `claimedAt` → primer `OPERATOR_*` del teléfono |
| Resolución | `claimedAt` → `resolvedAt` en conversaciones cerradas en el rango |
| Reapertura | eventos `REOPEN` en el rango (incl. follow-up CSAT manual) |
| Abandono | cola NEW/PENDING/REOPENED sin agente, anclaje `lastInboundAt`/`updatedAt` > `crm.metrics.abandonment-pending-hours` |
| Auto-resolución | cierres en rango sin `claimedAt` |
| Zona | `placeName` del ticket vinculado; si no hay ticket, suscripción o “Sin zona” |

## Configuración

`crm.metrics.*` en `application-dev.properties` / `application-prod.properties`.

## Tests

```text
src/test/.../CrmMetricsServiceTest.kt
src/test/.../CrmMetricsControllerTest.kt
```

## Riesgos / límites

1. CSAT operativo vs satisfacción: panel `/crm/csat` sigue siendo la fuente de encuestas; métricas operativas no cuentan cierres CSAT.
2. Zona = proxy `placeName`; no sustituye dimensión geográfica formal.
3. Agentes mostrados como `Agente #id` hasta enriquecer con catálogo.
