# WhatsApp backend — fases 0–2c (analytics + conversación)

Implementación backend completada el 2026-07-25.

## Migración SQL

Script manual: `src/main/resources/db/migration/V2__whatsapp_analytics_schema.sql`

Hibernate `ddl-auto=update` también aplica los cambios en dev. En prod ejecutar el script SQL antes del deploy si se prefiere control explícito.

## Webhook unificado

- **Único handler:** `WhatsAppWebhookController` en `/whatsapp/webhook`
- **Eliminado:** POST/GET `/whatsapp/webhook` duplicado en `WhatsAppController` (solo quedan endpoints de prueba técnica)

## Nuevos servicios

| Servicio | Responsabilidad |
|----------|-----------------|
| `WhatsAppMetaResponseParser` | Extrae `messages[0].id` (wamid) de respuesta Meta |
| `WhatsAppAnalyticsService` | Embudo, campañas y conversión desde BD local |
| `WhatsAppMetaAnalyticsClient` | Graph API: analytics, template/conversation/pricing |
| `WhatsAppTemplateSyncService` | Sync `message_templates` → `whatsapp_synced_template` |
| `WhatsAppAccountEventService` | Webhooks de gestión → `whatsapp_account_event` |
| `WhatsAppServiceWindowService` | Ventana 24h por teléfono |
| `WhatsAppConversationService` | Botones interactivos, mark-read, button_reply |
| `WhatsAppMediaDownloadService` | Descarga media Meta → `whatsapp.media-storage-dir` |
| `WhatsAppInboundPayloadParser` | Parseo text/interactive/image/document |

## Insights Meta (deploy manual)

Ver comentario en `deploy-prod-whatsapp.sh`. **No ejecutar en tests.**

## Endpoints backoffice nuevos/ampliados

- `GET /whatsapp/analytics/overview`
- `GET /whatsapp/analytics/campaigns`
- `GET /whatsapp/analytics/campaigns/{id}`
- `GET /whatsapp/analytics/conversion`
- `GET /whatsapp/analytics/meta/templates|conversations|pricing`
- `GET /whatsapp/account/health`
- `GET|POST /whatsapp/templates/sync`
- `GET /whatsapp/service-window/{phone}`
- `POST /whatsapp/conversations/{id}/mark-read`
- `GET /whatsapp/inbound-messages/{id}/media`
- `GET /whatsapp/inbound-messages` (movido desde WhatsAppController)

## Config

```properties
whatsapp.media-storage-dir=./data/whatsapp/media
```
