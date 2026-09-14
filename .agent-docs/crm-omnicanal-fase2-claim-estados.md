# CRM Omnicanal WhatsApp — Fase 2: Claim y estados

Fecha: 2026-08-02  
Rama: `feature/whatsapp-business-integration`

## Objetivo

Agregar ownership de conversaciones (`CrmConversation`) con claim atómico, transfer/release/resolve/reopen, notas internas, sync con el bot y presencia efímera STOMP.

## Modelo

Migración: `V6__whatsapp_crm_fase2_conversations.sql` (Flyway no activo; `ddl-auto=update` crea tablas en local).

| Tabla | Rol |
|-------|-----|
| `crm_conversation` | Agregado CRM por `channel+phone`; status `NEW/PENDING/ASSIGNED/RESOLVED/REOPENED`; `@Version`; índices status/assignee/lastInbound |
| `crm_assignment_event` | Historial CLAIM/RELEASE/TRANSFER/RESOLVE/REOPEN/AUTO_ASSIGN |
| `crm_internal_note` | Notas internas del agente |

## Endpoints (`SECRETARY|ADMIN`)

- `GET /crm/conversations` — filtros `status`, `assignedAgentId`, `priority`, `search`, `limit`
- `GET /crm/conversations/{id}`
- `GET /crm/conversations/by-phone/{phone}`
- `POST /crm/conversations/{id}/claim|release|transfer|resolve|reopen`
- `GET/POST /crm/conversations/{id}/assignments|notes`

Claim atómico: `UPDATE ... WHERE assigned_agent_id IS NULL AND status IN (NEW,PENDING,REOPENED)`.

## Sync bot

| Evento | Efecto CRM / bot |
|--------|------------------|
| Inbound | `touchInbound` → crea `NEW` o reabre `RESOLVED`→`REOPENED`; actualiza `lastInboundAt` |
| Handoff | `markPendingOnHandoff` → `PENDING` (si no hay assignee) |
| Reply operador | Exige assignee o ADMIN; pausa bot (`markWaitingForAdvisor`); `touchOutbound` |
| Resolve | Limpia assignee; opcionalmente `resumeBotAndTakeControl` |

## Unread vs claim

Claim **no** marca leído. Unread sigue gobernado por `mark-read` / `mark-all-read`.

## Tiempo real

Acciones CRM publican `CONVERSATION_UPDATED` vía `CrmEventPublisher` (incluye `status`, `assignedAgentId`, `assignedAgentName`).

Presencia efímera (sin persistir):

- Cliente → `/app/whatsapp/presence` (`VIEWING`/`TYPING`/`IDLE`)
- Broadcast → `/topic/whatsapp/presence`

## Tests

```text
CrmConversationServiceTest
CrmConversationControllerTest
CrmConversationClaimJpaTest (concurrencia H2)
WhatsAppHandoffServiceTest
WhatsAppInboundMessageServiceTest
WhatsAppConversationServiceTest
```

Resultado: verde.

## Fuera de alcance (Fases 3+)

Media saliente, reply-to, plantillas en hilo, QuickReply, LLM, tickets, CSAT, métricas.
