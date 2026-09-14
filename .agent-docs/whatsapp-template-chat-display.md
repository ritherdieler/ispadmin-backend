# WhatsApp: texto de plantillas en el hilo del chat

## Contexto

Los mensajes outbound por plantilla (recordatorio, validación de pago, etc.) se guardan en `whatsapp_message_log.message`. El backoffice muestra ese campo como `body` en el hilo de conversaciones.

## Comportamiento

1. Al **sincronizar plantillas** desde Meta (`WhatsAppTemplateSyncService.syncFromMeta`), se persiste el texto del componente `BODY` en `whatsapp_synced_template.body_text` (migración `V11__whatsapp_synced_template_body.sql`).
2. Al **arrancar** el backend con WhatsApp configurado, si falta `body_text` en alguna plantilla del catálogo, se intenta un sync automático (`WhatsAppTemplateBodyBootstrapRunner`).
3. Al **enviar** o **mostrar** una plantilla, si aún no hay BODY en BD se intenta **una sync desde Meta** (`WhatsAppTemplateDisplayService`) y luego se renderiza con placeholders Meta.
4. Si Meta no está disponible o el BODY sigue vacío, se usa **texto fallback legible** (`WhatsAppTemplateHumanFallback`) construido con los mismos parámetros (envíos **no** se bloquean por el preview).
5. Logs **legacy** (`metaName [param=valor]`) se re-renderizan al leer el hilo con la misma cadena: BODY Meta → fallback.

## Componentes

| Clase | Rol |
|-------|-----|
| `WhatsAppTemplateSyncService.extractBodyText` | Lee `components[].text` donde `type=BODY` |
| `WhatsAppTemplateBodyRenderer` | Sustitución named + parse legacy |
| `WhatsAppTemplateDisplayService` | Sync bajo demanda + preview/display |
| `WhatsAppTemplateHumanFallback` | Texto legible si no hay BODY |
| `WhatsAppThreadMessageMapper` | DTO del hilo con body renderizado |

## Notas

- Con WhatsApp configurado, el texto en chat coincide con Meta en cuanto exista `body_text` (sync automático o manual desde el hub).
- Si Meta cambia el copy, re-sincronizar; el historial legacy se muestra con el BODY **actual** en BD o con fallback.
- El cliente en WhatsApp siempre recibe la plantilla vía Cloud API; el preview en BD es para operadores en backoffice.
