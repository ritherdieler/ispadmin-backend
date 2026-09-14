# Refactor del menú del bot WhatsApp

Fecha: 2026-08-05

> El menú principal cambió después de este documento: con la opción `Enviar comprobante` pasa a ser una lista de cuatro filas y el enrutamiento vive en `WhatsAppConversationStateMachine`. Ver [whatsapp-bot-comprobante-maquina-estados-2026-08-05.md](./whatsapp-bot-comprobante-maquina-estados-2026-08-05.md).

## Resultado

El menú principal dejó de usar una lista que requería pulsar `Ver opciones`. Ahora usa tres reply buttons visibles:

1. `Reportar avería`
2. `Consultar deuda`
3. `Hablar con asesor`

La opción de instalación no forma parte del menú, pero su callback antiguo y su intención por texto continúan aceptándose por compatibilidad.

## Navegación

- Los submenús de soporte muestran únicamente las opciones relevantes como reply buttons.
- El footer indica que `MENÚ` y `ASESOR` pueden escribirse en cualquier momento.
- Las respuestas `1`, `2`, `3`, `A`, `B`, `C` y los nombres de las opciones se resuelven contra el estado actual.
- Un callback desconocido muestra el mensaje de selección inválida y no cae accidentalmente en soporte.
- Un primer mensaje con intención reconocible, como `Sin internet`, abre directamente el flujo correspondiente aunque la sesión sea nueva o haya expirado.

## Deuda y comprobantes

El resultado de deuda ofrece:

- `Ya pagué`
- `Menú principal`
- `Hablar con asesor`

Cualquier imagen o documento sigue considerándose comprobante de pago. Este comportamiento es global y también se aplica si el bot está pausado o la conversación espera asesor.

## WhatsApp Cloud API

Los mensajes interactivos incluyen soporte para:

- Footer.
- Contextual reply mediante `context.message_id`.
- Indicador de lectura y escritura antes de responder.
- Listas con varias secciones.
- Validación estricta de límites Meta.

Los límites se validan antes de enviar:

- Reply buttons: 1–3 opciones, id máximo 256 y título máximo 20.
- List messages: 1–10 secciones y 1–10 filas totales.
- Título de fila y sección: máximo 24.
- Descripción de fila: máximo 72.
- Footer: máximo 60.
- Body de botones: máximo 1024.
- Body de listas: máximo 4096.

El adaptador ya no recorta silenciosamente botones o filas con `take(3)` o `take(10)`. Una definición inválida falla antes de llamar a Meta.

## Arquitectura

- `WhatsAppBotMenuCatalog`: ids, labels y footers del menú.
- `WhatsAppInteractiveMessageValidator`: contrato de límites Meta.
- `WhatsAppBotTextSelectionResolver`: resolución de texto y números contra opciones visibles.
- `WhatsAppInboundMessageService.handleButtonReply`: ruta única compartida por clicks y selecciones escritas.
- `WhatsAppService`: payloads interactivos, contexto, footer, secciones y typing indicator.

## Compatibilidad

Se conservan los ids anteriores:

- `reportar_averia`
- `ver_deuda`
- `ya_pague`
- `hablar_asesor`
- `soporte`
- `solicitud_instalacion`
- `nav_back`
- `nav_home`

## Pruebas

- `WhatsAppInteractiveMessageValidatorTest`
- `WhatsAppBotTextSelectionResolverTest`
- `WhatsAppConversationServiceTest`
- `WhatsAppInboundMessageServiceTest`
- `CsatSurveyServiceTest`
- `WhatsAppInboundPayloadParserTest`
