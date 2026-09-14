# Correcciones UX menú WhatsApp — 2026-08-10

Tras la batería de menú (`.agent-docs/whatsapp-bot-menu-ux-review-2026-08-10.md`).

## Cambios

### P1 — Botones de diagnóstico obsoletos
- `WhatsAppConversationStateMachine`: `support_diag_*` solo cierra caso si `currentStep == SUPPORT_DIAG`.
- Fuera de ese paso → reenvía menú principal (sin handoff ni ticket `UNKNOWN`).

### P2 — Tildes / ortografía
- Copy del bot, defaults en `WhatsAppProperties` y `application-dev.properties` (horario, after-hours, menú, deuda, voucher, handoff, diagnóstico).

### P3 / P4 — Trato y deuda
- Deuda: sin `Estimado(a)`; saludo `Nombre, su saldo…`.
- Plan: `basico_wireless 50` → `Basico Wireless 50`.
- Ya pagué: `Gracias por avisarnos, {nombre}.` (usted); plural limpio `N facturas pendientes`.
- After-hours en Ya pagué: aclara que el voucher se revisa a primera hora cuando lo envíe.

### P7 — CTA post-handoff / ACK
- Cierre asesor (in/out hours) y ACK after-hours terminan con: `Si necesita algo más, escriba MENU.`

## Tests
- `WhatsAppConversationStateMachineTest` (stale diag)
- `WhatsAppConversationServiceTest` (copy, plan, plural, CTA)

## Deploy
- Requiere release backend.
- Si en VPS `/opt/gigafiber/.env` hay override de `whatsapp.auto-reply.after-hours-*` / `secretary-hours` sin tildes, actualizar esos valores al desplegar.
