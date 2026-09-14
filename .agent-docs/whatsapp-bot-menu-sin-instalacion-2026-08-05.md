# Menú WhatsApp: sin opción Instalación

Fecha: 2026-08-05

## Cambio

Se quitó **📦 Instalación** del menú principal interactivo del bot.

Opciones actuales:

1. 🛠️ Avería  
2. 💳 Deuda  
3. 🙋 Asesor  

## Notas

- El intent por texto (`instalacion`, `traslado`, etc.) y el handler de `solicitud_instalacion` se mantienen por si llega un mensaje libre o un botón antiguo.
- Archivo: `WhatsAppConversationService.buildMainMenuRows`
- Test: `WhatsAppConversationServiceTest.main menu list does not include installation option`
