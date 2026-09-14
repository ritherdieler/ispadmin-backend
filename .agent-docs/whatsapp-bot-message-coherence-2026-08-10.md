# WhatsApp bot: coherencia de mensajes (2026-08-10)

## Cambios

- After-hours compacto (sin repetir horario ni “primera hora”).
- ACK genérico fuera de horario: `buildAfterHoursAckMessage()` (sin “caso registrado”).
- Soft-ack post-voucher fuera de horario: silenciado (no segundo mensaje).
- Deuda con saldo: quita “mantenerte al día”; pide pagar y enviar comprobante.
- Familia pago/handoff/menú en **usted**.

## Actualización (mismo día)

Copy humanizado (tono natural): ver [`whatsapp-bot-humanizar-copy-2026-08-10.md`](./whatsapp-bot-humanizar-copy-2026-08-10.md).

## Copy after-hours (handoff) — vigente

```
✅ Su caso quedo registrado.

En este momento estamos fuera de horario laboral. Un asesor le atendera a primera hora por este mismo chat.

Nuestro horario es de lunes a viernes de 8:00 a.m. a 5:30 p.m. y sabados de 8:00 a.m. a 12:30 p.m.
```

## Copy after-hours (ACK genérico) — vigente

```
Recibido, gracias.

En este momento estamos fuera de horario laboral. Le responderemos a primera hora.

Nuestro horario es de lunes a viernes de 8:00 a.m. a 5:30 p.m. y sabados de 8:00 a.m. a 12:30 p.m.
```

## Fuera de alcance

Plantillas quick-reply de agentes (visita 2–6pm, mes de julio hardcodeado).
