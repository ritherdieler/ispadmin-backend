# ACK suave post-voucher (WhatsApp bot)

Fecha: 2026-08-10

## Problema

Tras confirmar un comprobante el bot dejaba el paso en `MAIN_MENU` sin reenviar botones. Como `MAIN_MENU` cuenta como menú interactivo pendiente, cualquier texto libre (`ACK` / `GREETING` / `UNKNOWN`) disparaba `[MENU_INVALID]`.

## Comportamiento nuevo

1. Imagen o PDF de comprobante → `CONFIRM_PAYMENT_PROOF` y paso `AWAITING_RECEIPT_REVIEW` (excepto si ya está en `ESPERANDO_ASESOR`, donde el paso no cambia).
2. Texto libre en `AWAITING_RECEIPT_REVIEW` con intent `ACK`, `GREETING` o `UNKNOWN` → `ACK_RECEIPT_PENDING` (`[VOUCHER_PENDING]`), sin menú y sin `MENU_INVALID`.
3. Intents claros (`DEBT_INQUIRY`, `SUPPORT`, `HUMAN_ESCALATION`, `PAYMENT_CLAIM`, etc.) siguen su flujo normal.
4. Botones antiguos de WhatsApp siguen válidos vía la FSM.

## Mensaje soft ACK

`Ya tenemos su comprobante en revision. Un asesor le confirmara en breve. Gracias.`

## Archivos

- `WhatsAppConversationStep.AWAITING_RECEIPT_REVIEW`
- `WhatsAppBotAction.ACK_RECEIPT_PENDING`
- `WhatsAppConversationStateMachine.onPaymentProof` / `onUnmatchedText`
- `WhatsAppInboundMessageService.handleText` (guarda post-voucher) y `executeBotAction`
- `WhatsAppConversationService.buildReceiptPendingAckResponse`
- `hasPendingInteractiveMenu` **no** incluye `AWAITING_RECEIPT_REVIEW`

## Docs relacionadas

- `.agent-docs/whatsapp-bot-comprobante-maquina-estados-2026-08-05.md`
- `local-docs/whatsapp-local-webhook.md`
