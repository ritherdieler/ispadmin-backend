# WhatsApp CRM — orden de bandeja por último inbound (2026-08-04)

## Cambio

`GET /whatsapp/conversations` ahora expone `lastInboundAt` en `WhatsAppConversationSummaryDto` y ordena la lista por:

1. `lastInboundAt` descendente (conversaciones sin inbound al final)
2. `lastMessageAt` descendente como desempate

`lastMessageAt` se mantiene (último inbound **o** outbound) para compatibilidad y preview; **ya no manda el orden**.

## Motivo

Alineación con cola CRM: un mensaje saliente del bot/plantilla no debe empujar un contacto por encima de clientes que escribieron más recientemente.

## Archivos

- `dto/WhatsAppConversationDto.kt`
- `service/whatsapp/WhatsAppConversationQueryService.kt`
- `WhatsAppConversationQueryServiceTest` (caso outbound reciente vs inbound más nuevo)

## Frontend pareja

Backoffice: vistas `Por atender | Mías | Equipo | Resueltas | Todas` + `sortConversationsForCrmInbox` por `lastInboundAt`. Ver `ispadmin-backoffice/.agent-docs/whatsapp-hub-ux.md`.
