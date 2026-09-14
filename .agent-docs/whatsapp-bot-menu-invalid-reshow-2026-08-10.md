# Audit: MENU_INVALID sin menú visible (Saul Leon Laguna) — 2026-08-10

## Caso

1. 19:03 Registrar pago → menú comprobante
2. 19:04 Hablar con asesor → handoff
3. 19:44 Usuario envía `.` → `[MENU_INVALID]` “toca una opción del menú” **sin mostrar menú**
4. 19:45 `Gghh` → `[ASESOR:FALLBACK]` after-hours

## Causa

Con menú “pendiente” (o tras reactivar el bot), texto no reconocido disparaba `UnmatchedText` → `INVALID_SELECTION`, que solo enviaba el guardrail de texto y **no reenviaba** botones/lista.

Tras ~30 min en `ESPERANDO_ASESOR`, el auto-resume deja el paso en `MAIN_MENU`; un `.`/`UNKNOWN` en ese contexto podía caer en el mismo guardrail vacío.

## Fix

`WhatsAppConversationStateMachine.onUnmatchedText`:

| Paso | Acción |
|------|--------|
| MAIN_MENU | Reenviar menú principal |
| SUPPORT_MENU / SUPPORT_DIAG | Reenviar menú soporte |
| DEBT_VIEW | Reenviar menú deuda |
| AWAITING_PAYMENT_PROOF | Recordatorio comprobante (sin cambio) |
| AWAITING_RECEIPT_REVIEW | Soft-ack (sin cambio; fuera de horario silenciado) |
| else | Menú principal |

Callback desconocido: también reenvía menú principal (ya no `INVALID_SELECTION`).

## Release

`1.0.3+…` tras deploy de este fix.
