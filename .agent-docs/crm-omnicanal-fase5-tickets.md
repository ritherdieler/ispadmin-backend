# CRM Omnicanal WhatsApp — Fase 5: Tickets y perfil unificado

Fecha: 2026-08-02  
Rama: `feature/whatsapp-business-integration`

## Prerrequisito resuelto: Flyway V7/V8

Fases 3 y 4 corrieron en paralelo y ambas generaron `V7__…`. Quedó:

| Versión | Archivo |
|---------|---------|
| V7 | `V7__whatsapp_crm_fase3_chat.sql` (reply-to, media, quick replies) |
| V8 | `V8__whatsapp_crm_fase4_llm.sql` (integration settings, handoff_summary) |
| V9 | `V9__whatsapp_crm_fase5_tickets.sql` (`ticket_conversation_link`) |

## Qué se añadió

| Pieza | Rol |
|-------|-----|
| `TicketConversationLink` | Vínculo único ticketId ↔ conversationId |
| `CrmTicketLinkService` | Crear ticket desde chat, dedup con `ticketDedupHours`, lista por teléfono/conversación, SLA heurístico |
| `CrmTicketController` | `GET/POST /crm/conversations/{id}/tickets`, `GET /crm/tickets/by-phone/{phone}`, deep-link |
| Bot `TICKET_STATUS` | Consulta estado de tickets abiertos |
| Cierre diagnóstico soporte | Crea ticket guiado (categoría por issue) con dedup + link CRM |
| `CrmTicketCustomerNotifyService` | Aviso WhatsApp de avances si ventana 24h abierta |
| Contexto ampliado | Pagos recientes, órdenes instalación, historial CRM, tickets + SLA |
| `PUT /assistanceTicket/{id}/status` | `IN_PROGRESS` / `RESOLVED` / `REOPEN` / cierre |

## Dedup

Usa `whatsapp.autoReply.ticketDedupHours` (default 24). Busca tickets abiertos (`PENDING|ASSIGNED|IN_PROGRESS|REOPEN`) mismo teléfono + categoría dentro de la ventana; si existe, reutiliza y asegura el link.

## SLA (heurística viable)

Sin campo SLA en BD. Se marca `slaBreached` si el ticket sigue abierto y supera:

- prioridad ≥ 10 (Alta): 24 h
- prioridad ≥ 5 (Media): 48 h
- resto: 72 h

## Notificación WhatsApp al cliente

- Dentro de ventana 24h: texto libre con estado/técnico.
- Fuera de ventana: se omite (log informativo). **Requiere plantilla Meta APPROVED** para proactividad (riesgo Fase 6 CSAT).

## Tests

```text
CrmTicketLinkServiceTest
CrmTicketCustomerNotifyServiceTest
WhatsAppInboundIntentRouterTest (TICKET_STATUS)
WhatsAppConversationQueryServiceTest
WhatsAppInboundMessageServiceTest
```

## Fuera de alcance (en Fase 5)

Fases 6 (CSAT) y 7 (métricas). Ver [crm-omnicanal-fase6-csat.md](./crm-omnicanal-fase6-csat.md).
