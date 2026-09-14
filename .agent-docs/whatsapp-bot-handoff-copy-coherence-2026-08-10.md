# WhatsApp bot: coherencia de copy por tipo de handoff (2026-08-10)

## Problema

Pedir **Hablar con asesor** desde el menú decía “Su caso fue/quedó registrado”. Eso suena a ticket de avería; el cliente solo pidió hablar con alguien.

## Decisión

| Tipo | Cuándo | Lead al cliente |
|------|--------|-----------------|
| `ADVISOR_QUEUE` | Menú / texto ASESOR / escalación LLM | `Su solicitud quedo registrada.` |
| `SUPPORT_CASE` | Cierre de diagnóstico de soporte (sí hay ticket) | `Su caso quedo registrado.` |
| `INSTALLATION` | Solicitud de instalación | `Su solicitud de instalacion quedo registrada.` |

API: `WhatsAppHandoffCopyKind` + `buildHumanHandoffClientMessage(now, kind)`.

## Otras incoherencias corregidas

| Emisión | Antes | Ahora |
|---------|-------|-------|
| ACK dentro de horario (`buildAckResponse`) | Reutilizaba texto de menú inválido (“seleccione una opción…”) | `Gracias. Si necesita algo mas, puede escribir MENU o ASESOR.` |
| Tickets vacíos (`formatStatusReply`) | “use el menu” | “toque *Menu principal*” |

## Inventario de auto-replies del bot (cliente)

Markers `[…]` solo van al log, no al WhatsApp (salvo rutas que no sanitizan; el texto visible no incluye markers de soporte).

| Flujo | Builder / origen | Tono |
|-------|------------------|------|
| Menú saludo / sin saludo | `buildMainMenuBody` | usted |
| Footer global / pago | `WhatsAppBotMenuCatalog` | usted |
| Soporte combo / simple | `supportEntryBody` | usted |
| Diagnóstico fibra / coax / TV | `diagnosticQuestion` | usted |
| Deuda | `buildDebtResponse` | usted |
| Pedir / recordar comprobante | `buildPaymentProofRequest` / `Reminder` | usted |
| Ya pagué al día / con deuda / corte | `buildPaidResponse` | usted |
| Voucher / soft-ack | `buildVoucherReceivedResponse` / `buildReceiptPendingAckResponse` | usted + after-hours Lote 1 |
| Handoff asesor | `ADVISOR_QUEUE` | solicitud, no caso |
| Handoff soporte | `SUPPORT_CASE` | caso (ticket real) |
| Handoff instalación | `INSTALLATION` | solicitud de instalación |
| ACK after-hours | `buildAfterHoursAckMessage` | sin caso/solicitud |
| ACK in-hours | `buildAckResponse` | gracias + MENU/ASESOR |
| Selección inválida | `invalidInteractiveSelectionText` | usted |
| Estado tickets | `CrmTicketLinkService.formatStatusReply` | usted |
| Cuenta no encontrada | `accountNotFoundMessage` | usted |

## Fuera de alcance

- Quick replies de agentes (`WhatsAppQuickReplyCatalog`)
- Plantillas Meta / mensajes de operador

## Verificación

```bash
./mvnw test -Dtest=WhatsAppConversationServiceTest,WhatsAppInboundMessageServiceTest,CrmTicketLinkServiceTest
```

Sin deploy hasta autorización.
