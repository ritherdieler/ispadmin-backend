# Comprobante de pago guiado y máquina de estados del bot WhatsApp

Fecha: 2026-08-05

## Resultado

El bot incorpora la opción `Registrar pago`, que deja la conversación esperando una imagen o un PDF, y el enrutamiento de la conversación pasa a resolverse en una tabla explícita de transiciones (`WhatsAppConversationStateMachine`) en lugar de cadenas de `if` repartidas entre servicios.

## Menú principal

Ahora ofrece cuatro entradas. Como Meta limita los reply buttons a tres, el menú se envía como list message con descripciones:

| Opción | Id | Descripción |
|--------|----|-------------|
| Reportar avería | `reportar_averia` | Internet o TV con problemas |
| Consultar deuda | `ver_deuda` | Saldo pendiente y formas de pago |
| Registrar pago | `enviar_comprobante` | Adjunta tu voucher (foto o PDF) |
| Hablar con asesor | `hablar_asesor` | Te atiende una persona del equipo |

Los submenús de soporte, deuda y comprobante siguen usando reply buttons directos porque tienen tres opciones o menos.

## Flujo de comprobante

1. El cliente toca `Registrar pago`, escribe `comprobante`/`voucher`/`registrar pago`/`ya pagué`, o toca `Ya pagué` en el menú de deuda.
2. El bot responde con las instrucciones de pago y deja el chat en `AWAITING_PAYMENT_PROOF`, con botones `Menú principal` y `Hablar con asesor`.
3. Si llega una imagen o un documento, el bot confirma la recepción y pasa a `AWAITING_RECEIPT_REVIEW` (sin reenviar menú).
4. Si llega texto libre después del voucher (`ACK`/`GREETING`/`UNKNOWN`), responde un ACK suave (`[VOUCHER_PENDING]`) en lugar de `MENU_INVALID`.
5. Si llega texto que no corresponde a ninguna opción mientras aún espera el archivo, el bot recuerda que sigue esperando el archivo.

Cualquier imagen o PDF continúa tratándose como comprobante en cualquier estado, incluso con el bot pausado o esperando asesor. Fuera de `ESPERANDO_ASESOR` el paso pasa a `AWAITING_RECEIPT_REVIEW`; en espera de asesor el paso no cambia.

## Máquina de estados

`WhatsAppConversationStateMachine.next(paso, evento)` devuelve una acción, el paso siguiente y, cuando corresponde, el motivo de handoff.

Eventos: `ButtonSelected(id)`, `PaymentProofReceived`, `UnmatchedText`.

| Paso | Evento | Acción | Paso siguiente |
|------|--------|--------|----------------|
| cualquiera | `nav_home` | `SHOW_MAIN_MENU` | `MAIN_MENU` |
| `SUPPORT_DIAG` | `nav_back` | `SHOW_SUPPORT_MENU` | `SUPPORT_MENU` |
| resto | `nav_back` | `SHOW_MAIN_MENU` | `MAIN_MENU` |
| cualquiera | `reportar_averia` / `soporte` | `SHOW_SUPPORT_MENU` | `SUPPORT_MENU` |
| cualquiera | `support_issue_*` | `SHOW_SUPPORT_DIAGNOSTIC` | `SUPPORT_DIAG` |
| cualquiera | `support_diag_*` | `CLOSE_SUPPORT_DIAGNOSTIC` | `ESPERANDO_ASESOR` (`support_diagnostic`) |
| cualquiera | `ver_deuda` | `SHOW_DEBT_MENU` | `DEBT_VIEW` |
| cualquiera | `ya_pague` | `SHOW_PAID_STATUS` | `AWAITING_PAYMENT_PROOF` |
| cualquiera | `enviar_comprobante` | `REQUEST_PAYMENT_PROOF` | `AWAITING_PAYMENT_PROOF` |
| cualquiera | `hablar_asesor` | `ESCALATE_TO_ADVISOR` | `ESPERANDO_ASESOR` (`advisor_request`) |
| cualquiera | `solicitud_instalacion` | `ESCALATE_TO_ADVISOR` | `ESPERANDO_ASESOR` (`installation_request`) |
| cualquiera | callback desconocido | `INVALID_SELECTION` | sin cambio |
| cualquiera excepto `ESPERANDO_ASESOR` | comprobante recibido | `CONFIRM_PAYMENT_PROOF` | `AWAITING_RECEIPT_REVIEW` |
| `ESPERANDO_ASESOR` | comprobante recibido | `CONFIRM_PAYMENT_PROOF` | sin cambio |
| `AWAITING_PAYMENT_PROOF` | texto sin coincidencia | `REMIND_PAYMENT_PROOF` | sin cambio |
| `AWAITING_RECEIPT_REVIEW` | texto sin coincidencia | `ACK_RECEIPT_PENDING` | sin cambio |
| resto | texto sin coincidencia | `INVALID_SELECTION` | sin cambio |

Los callbacks de Meta se aceptan aunque el paso persistido haya caducado, porque los botones antiguos siguen siendo pulsables en el chat del cliente. Las guardas por paso se aplican donde la respuesta correcta depende del contexto, como `nav_back`.

### Persistencia del paso

`WhatsAppBotAction.sendsInteractiveMenu` indica que el envío del menú ya persiste su propio paso. El orquestador solo escribe `nextStep` para acciones de texto plano y nunca cuando hay `handoffReason`, porque en ese caso `WhatsAppHandoffService` es el dueño del estado.

## Integración

- `WhatsAppInboundMessageService.handleBotEvent` es la única ruta para clicks, selecciones escritas, comandos `MENÚ`/`VOLVER` y medios entrantes.
- `executeBotAction` traduce cada acción a un envío concreto; `applyTransitionEffects` aplica ticket guiado, handoff, alerta a secretaría y persistencia de paso.
- `WhatsAppConversationService.sendBackNavigation` se eliminó: la decisión de `Volver` vive ahora en la máquina de estados.
- `WhatsAppBotMenuCatalog` centraliza ids, títulos, descripciones y prefijos `support_issue_` / `support_diag_`.

## Estado persistido

`WhatsAppConversationStep` incorpora `AWAITING_PAYMENT_PROOF` y `AWAITING_RECEIPT_REVIEW`. La columna `current_step` es `varchar(64)` gestionada por Hibernate y su converter cae a `MAIN_MENU` ante valores desconocidos, por lo que no hace falta migración.

`hasPendingInteractiveMenu` incluye `AWAITING_PAYMENT_PROOF` para que las respuestas `1`, `2` y los títulos escritos funcionen mientras se espera el comprobante. **No** incluye `AWAITING_RECEIPT_REVIEW`, para evitar `MENU_INVALID` tras el voucher.

## Intenciones por texto

`PAYMENT_CLAIM` reconoce además `comprobante`, `voucher` y `constancia de pago`, y ahora responde con el menú interactivo de comprobante en lugar de solo texto.

## Pruebas

- `WhatsAppConversationStateMachineTest`: tabla de transiciones y persistencia de paso por acción.
- `WhatsAppConversationServiceTest`: menú principal con cuatro filas y descripciones, solicitud de comprobante, estado de pago y selección por texto en `AWAITING_PAYMENT_PROOF`.
- `WhatsAppInboundMessageServiceTest`: botón de comprobante, confirmación con PDF durante la espera y recordatorio ante texto.
- `WhatsAppInboundIntentRouterTest`: sinónimos de comprobante.

Suite ejecutada: `./mvnw -o test -Dtest='WhatsApp*Test'` con 230 pruebas en verde.
