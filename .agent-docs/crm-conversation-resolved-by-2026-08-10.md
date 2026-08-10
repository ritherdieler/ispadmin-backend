# CRM: resolved_by_agent_id (2026-08-10)

## Campo

- Tabla `crm_conversation.resolved_by_agent_id` — usuario (`User.id`) que ejecutó **Resolver**.
- Se setea en `CrmConversationService.resolve` con el `agentId` autenticado (assignee o ADMIN).
- Al resolver se limpia `assigned_agent_id`; el operador queda en `resolved_by_agent_id`.

## Evento RESOLVE

- `from_user_id`: assignee anterior (liberado).
- `to_user_id`: operador que resolvió (antes era `NULL`).

## API / realtime

- `CrmConversationDto`: `resolvedByAgentId`, `resolvedByAgentName`.
- `WhatsAppConversationSummaryDto`: `resolvedByAgentId` (nombre vía merge CRM o payload realtime).
- `CONVERSATION_UPDATED`: `resolvedByAgentId`, `resolvedByAgentName`.

## Histórico

Sin backfill: cierres anteriores al deploy no tienen `resolved_by_agent_id`.

Migración: `V16__crm_conversation_resolved_by.sql`.
