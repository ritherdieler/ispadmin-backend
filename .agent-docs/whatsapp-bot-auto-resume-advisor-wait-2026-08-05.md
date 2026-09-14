# Auto-reanudación del bot tras espera de asesor

Fecha: 2026-08-05

## Comportamiento

Cuando un chat queda en `ESPERANDO_ASESOR` (`botPaused=true`) por handoff (avería, instalación, pedir asesor, etc.), el bot **no** responde a nuevos mensajes del cliente.

Si el estado pausado tiene más de **30 minutos** (`updatedAt` del `whatsapp_chat_state`) y el cliente vuelve a escribir:

1. `WhatsAppChatStateService.beginInboundInteraction` reanuda el bot (`BOT_ACTIVE`, step `MAIN_MENU`).
2. Marca `autoResumedFromAdvisorWait=true` e `isNewOrExpired=true`.
3. `WhatsAppInboundMessageService` sincroniza handoff (`resumeBotAndTakeControl` con razón `auto_resume_advisor_wait`).
4. Se envía el menú principal con saludo (mismo camino que sesión nueva/expirada).

Antes de los 30 minutos el bot sigue en silencio y el mensaje solo entra a la bandeja CRM.

## Configuración

| Propiedad | Default | Descripción |
|-----------|---------|-------------|
| `whatsapp.auto-reply.advisor-wait-timeout-minutes` | `30` | Minutos en `ESPERANDO_ASESOR` antes de auto-reanudar al siguiente inbound |

Relacionado: `whatsapp.auto-reply.session-timeout-minutes` (10) aplica solo a sesiones `BOT_ACTIVE`, no a pausa por asesor.

## Archivos

- `WhatsAppChatStateService` — detección y reanudación de estado
- `WhatsAppInboundMessageService` — sync handoff + menú
- `WhatsAppAutoReplyProperties.advisorWaitTimeoutMinutes`
