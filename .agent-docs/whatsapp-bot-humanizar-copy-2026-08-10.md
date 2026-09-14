# WhatsApp bot: humanizar auto-replies (2026-08-10)

## Objetivo

Reescribir los auto-replies del bot con tono natural en **usted** (estilo voucher after-hours), sin jerga de sistema (“figura”, “Fuera de horario laboral: …”, “Horario:”).

Sin deploy a producción en esta iteración (pendiente autorización explícita).

## Seguimiento

Handoff menú asesor vs soporte (solicitud vs caso): [`whatsapp-bot-handoff-copy-coherence-2026-08-10.md`](./whatsapp-bot-handoff-copy-coherence-2026-08-10.md).

## Tono

- Tratamiento usted
- Frases completas y cercanas
- Horario en oración: `Nuestro horario es de …`
- Markers `[VOUCHER]`, `[ASESOR]`, etc. solo en log

## Ejemplo canónico (voucher after-hours)

```
Recibimos su comprobante. Gracias.

En este momento estamos fuera de horario laboral. Un asesor lo revisara a primera hora.

Nuestro horario es de lunes a viernes de 8:00 a.m. a 5:30 p.m. y sabados de 8:00 a.m. a 12:30 p.m.
```

## Archivos

| Archivo | Cambio |
|---------|--------|
| `WhatsAppConversationService.kt` | Copy lotes 1–3; `businessHoursSentence()`; follow-up after-hours opcional |
| `WhatsAppProperties.kt` / `application-dev.properties` | `secretaryHours`, `afterHoursMessage`, `afterHoursHumanFollowUpMessage` |
| `WhatsAppBotMenuCatalog.kt` | Footer global + descripción asesor |
| Tests asociados | Asserts al nuevo wording |

## Lotes aplicados

### Lote 1 — After-hours / handoff

- Voucher / soft-ack / handoff / ACK genérico: “En este momento estamos fuera de horario…” + horario en oración
- `Su caso quedo registrado.` (antes “fue”)
- Props: `afterHoursMessage` / `afterHoursHumanFollowUpMessage` actualizados

### Lote 2 — Pago / deuda / comprobante

- Voucher in-hours: confirmación por este chat
- Pedir / recordar comprobante en usted
- Deuda: cierre con CCI/BCP o Yape + envío de comprobante
- Ya pagué al día / con deuda / corte: sin “figura”
- Cuenta no encontrada: “comuniquese con Secretaria”

### Lote 3 — Menú / soporte / diagnóstico

- Saludo: `Hola. Le atiende el asistente virtual… ¿En que podemos ayudarle hoy?`
- Menú / soporte / diagnóstico en usted
- Footer: `Puede escribir MENU o ASESOR en cualquier momento`
- Asesor: `Lo conectamos con una persona del equipo`
- Handoff in-hours: caso quedó registrado + puede llamar a Secretaría

## Fuera de alcance

- Quick replies de agentes (`WhatsAppQuickReplyCatalog`)
- Markers de log

## Verificación

```bash
./mvnw test -Dtest=WhatsAppConversationServiceTest,WhatsAppInboundMessageServiceTest,WhatsAppConversationStateMachineTest
```
