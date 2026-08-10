# WhatsApp Comprobantes (inbox) — 2026-08-10

## Regla

La pestaña **Comprobantes** lista conversaciones con un inbound **imagen** o **PDF** cuyo `created_at` es **posterior al último `resolvedAt`** de la conversación CRM.

- No cuentan: audio, video, sticker, documentos no-PDF.
- `resolvedAt` se conserva al reabrir / inbound tras resolve (último cierre).
- Flag API: `hasPendingReceipt` en el summary; vista `view=receipts`.
- `hasMedia` del hilo sigue siendo cualquier adjunto.

## Archivos clave

- `WhatsAppThreadMessageMapper.inboundIsPaymentProof`
- `WhatsAppInboxViewPolicy.hasPendingReceipt`
- `WhatsAppInboundMessageRepository.findLatestMediaAtByPhoneIn` (solo image/PDF)
- `CrmConversationService` (no limpia `resolvedAt` en reopen/touchInbound/handoff)
