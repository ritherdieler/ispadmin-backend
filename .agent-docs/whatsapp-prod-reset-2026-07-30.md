# Reset WhatsApp prod — 2026-07-30

## Objetivo

Dejar la feature WhatsApp en prod como primer uso (sin historial de chats/webhooks).

## Tablas vaciadas (`ispadmin`)

| Tabla | Antes |
|-------|------:|
| `whatsapp_message_log` | 12 |
| `whatsapp_inbound_message` | 9 |
| `whatsapp_webhook_event` | 53 |
| `whatsapp_phone_session` | 1 |
| `whatsapp_account_event` | 0 |
| `whatsapp_synced_template` | 0 |

Todas quedaron en **0** filas. Prueba de insert en `whatsapp_webhook_event` asignó `id=1` (secuencia reiniciada).

No se tocó ninguna tabla de negocio (suscripciones, pagos, etc.).

## Media

No había directorio de media WhatsApp con archivos en el contenedor Tomcat.
