# Deduplicación ACK de voucher WhatsApp — 2026-08-10

## Problema
Dos imágenes/PDF de comprobante en <90s generaban dos mensajes `Recibimos su comprobante…` (`[VOUCHER]`).

## Fix
- `WhatsAppConversationService.hasRecentVoucherAck(phone)`: busca AUTO_REPLY con mensaje que empieza por `[VOUCHER]` en los últimos **3 minutos**.
- `WhatsAppInboundMessageService` en `CONFIRM_PAYMENT_PROOF`: si hay ACK reciente, omite el envío pero sigue aplicando la transición (paso / efectos).
- Repo: `existsByPhoneAndMessageTypeAndMessageStartingWithAndCreatedAtAfter`.

## Tests
- `second voucher within dedup window does not send another ACK`
- `image and pdf remain global payment proofs` (1 ACK para 2 medias)
- `hasRecentVoucherAck looks for recent VOUCHER auto reply`

## Nota deploy
Requiere release backend. No hay variable de entorno nueva.
