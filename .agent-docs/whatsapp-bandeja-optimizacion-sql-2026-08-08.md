# Optimización bandeja WhatsApp (`GET /whatsapp/conversations`)

Fecha: 2026-08-08

## Problema

La traza `d62db3d03169a723ff2f943ab24aa3d8` mostraba ~127 spans DB con N+1 masivo sobre `whatsapp_synced_template WHERE name=?`, carga completa de historial vía `findByPhoneIn` y `Subscription` EAGER solo para el nombre.

## Cambios implementados

1. **Plantillas**: `displayStoredMessages` con `findByNameIn` por lote (sin caché en JVM) en `WhatsAppTemplateDisplayService`.
2. **Agregación SQL**: latest inbound/outbound, unread, media, subscription y button reply por teléfono (sin `findByPhoneIn` de todo el historial).
3. **Proyección subscription**: `findNameProjectionsByIdIn` (id + firstName + lastName).
4. **Paginación real del ranking**: para `view=ALL`, `findRecentActivePhones(pageSize)` (máx. 100). Otras vistas usan ventana hasta 10× pageSize (tope 500) y filtran.
5. **Vistas server-side**: param `view` + `agentId`; regla Comprobantes = pendiente de validar (`hasPendingReceipt`: media posterior a `resolvedAt` y status ≠ RESOLVED).
6. **Conteos**: `GET /whatsapp/conversations/view-counts` (chips + `totalUnread`). Badge deja de usar `getConversations(limit=200)` como camino principal.
7. **Front**: filtro receipts usa `hasPendingReceipt`; inbox pide `limit=50` + `view`; pills usan view-counts.

## Índices / V16

No se añadió migración V16: índices existentes (`phone, created_at`, `phone, read_at`, `idx_wa_synced_template_name`) cubren las queries agregadas. Validar en staging con `EXPLAIN` sobre:

- `findRecentActivePhones`
- `findLatestInboundByPhoneIn` / `findLatestOutboundByPhoneIn`
- `countUnreadByPhoneIn` / `findLatestMediaAtByPhoneIn`

## Tabla inbox opcional

`crm_conversation` ya tiene `last_inbound_at` / `last_outbound_at` / `resolved_at`, pero no cubre todos los teléfonos sin fila CRM. Una `whatsapp_inbox_phone` denormalizada sigue siendo el siguiente paso si el GROUP BY de ranking supera ~7–20 ms con volumen real. No se creó en este cambio.

## Verificación

- Backend: `./mvnw -Dtest=WhatsAppTemplateDisplayServiceTest,WhatsAppInboxViewPolicyTest,WhatsAppConversationQueryServiceTest test`
- Front: tests de `crmInbox`, `useWhatsAppUnreadBadge`, `WhatsAppWebInbox`
- Traza post-deploy: spans de `whatsapp_synced_template` ~0–2 por request; ranking LIMIT ≈ pageSize en vista ALL.
