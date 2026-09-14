# WhatsApp: mensaje único fuera de horario (intervención humana)

Fecha: 2026-08-10

## Objetivo

Cuando el cliente solicita un asesor o el flujo requiere intervención humana **fuera del horario laboral**, el bot comunica que será atendido a la **primera hora dentro del horario laboral**, en todos los caminos relevantes (no solo escalación por texto).

## Horario laboral (`America/Lima`)

| Días | Ventana |
|------|---------|
| Lun–Vie | 08:00 – 17:30 (fin exclusivo) |
| Sáb | 08:00 – 12:30 (fin exclusivo) |
| Dom | cerrado |

Config: `whatsapp.auto-reply.business-hours=MON-FRI|08:00-17:30;SAT|08:00-12:30`

## API central

En `WhatsAppConversationService`:

- `buildHumanHandoffClientMessage(now?)` — handoff explícito a asesor (dentro: cierre inmediato; fuera: plantilla after-hours).
- `buildHumanFollowUpClientMessage(inHoursText, afterHoursLead, now?)` — promesas de revisión humana (voucher / comprobante / “ya pagué” con pendientes).
- `buildAfterHoursAckMessage()` — ACK genérico fuera de horario (sin “caso registrado”).

Plantillas en `WhatsAppAutoReplyProperties`:

- `afterHoursMessage`
- `afterHoursHumanFollowUpMessage`
- `secretaryHours`

## Caminos cubiertos

| Situación | Mensaje fuera de horario |
|-----------|--------------------------|
| Texto pide asesor / escalación LLM | Handoff “primera hora” + pausa bot |
| Botón “Hablar con asesor” | Idem |
| Instalación (texto o botón legacy) | Idem |
| Cierre de diagnóstico de soporte | Idem + ticket guiado si aplica |
| Comprobante recibido | Lead corto + follow-up “primera hora” |
| ACK/gracias post-voucher | Sin segundo mensaje (silenciado) |
| ACK genérico | “Recibido, gracias.” + primera hora |
| “Ya pagué” con facturas pendientes | Misma promesa (sin “a la brevedad”) |

## Copy compacto (vigente)

Handoff:

```
✅ Su caso quedo registrado.

En este momento estamos fuera de horario laboral. Un asesor le atendera a primera hora por este mismo chat.

Nuestro horario es de lunes a viernes de 8:00 a.m. a 5:30 p.m. y sabados de 8:00 a.m. a 12:30 p.m.
```

Ver también: `whatsapp-bot-message-coherence-2026-08-10.md`, `whatsapp-bot-humanizar-copy-2026-08-10.md`.

## Tests

`WhatsAppBusinessHoursCheckerTest`, `WhatsAppConversationServiceTest`, `WhatsAppInboundMessageServiceTest`.
