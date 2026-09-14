# CRM Omnicanal WhatsApp — Fase 3: Chat completo

Fecha: 2026-08-02  
Rama: `feature/whatsapp-business-integration`

## Objetivo

Completar el chat de agente: media saliente, reply-to, plantillas desde el hilo (ventana 24h cerrada), QuickReply y borradores locales, respetando ownership (assignee o ADMIN).

## Migración

`V7__whatsapp_crm_fase3_chat.sql`

| Cambio | Rol |
|--------|-----|
| `whatsapp_message_log.reply_to_log_id` | Referencia reply-to |
| `media_meta_id`, `media_mime_type`, `media_stored_path`, `media_filename` | Media saliente + descarga autenticada |
| `retry_count` | Reintento controlado (máx. 3) |
| `crm_quick_reply` | Respuestas rápidas (global o por usuario) |

## Endpoints nuevos

| Método | Ruta | Notas |
|--------|------|-------|
| POST | `/whatsapp/conversations/{phone}/media` | multipart `file` + `caption` + `replyToMessageId`; exige assignee/ADMIN y ventana 24h abierta |
| POST | `/whatsapp/conversations/{phone}/template` | body `{ templateCode }`; exige assignee/ADMIN; usable con ventana cerrada |
| POST | `/whatsapp/conversations/{phone}/outbound/{logId}/retry` | Solo `FAILED` y `retryCount < 3` |
| GET | `/whatsapp/outbound-messages/{id}/media` | Descarga autenticada del archivo local |
| GET/POST/PUT/DELETE | `/crm/quick-replies` | CRUD; global solo ADMIN |

`POST .../reply` acepta `replyToMessageId` (`inbound:{id}`, `outbound:{id}` o wamid).

## Ownership

Todas las vías de envío (texto, media, plantilla, retry) llaman `CrmConversationService.assertCanReply` — misma regla que Fase 2.

## Restricciones Cloud API (documentadas)

Ver [`whatsapp-meta-cloud-api.md`](./whatsapp-meta-cloud-api.md) §Límites operativos.

- Soportado saliente: texto, imagen jpeg/png (5 MB), audio (16 MB), documento (100 MB), plantillas.
- **No** implementado: stickers, reacciones, typing indicator (no hay API Business confiable para el hub).

## Tests

```text
WhatsAppMediaConstraintsTest
WhatsAppConversationServiceTest (reply-to, media, template, retry)
CrmQuickReplyServiceTest
WhatsAppTemplateDeliveryServiceTest
```

Resultado: verde.

## Fuera de alcance

Fases 4–7 (LLM settings, tickets, CSAT, métricas operativas).
