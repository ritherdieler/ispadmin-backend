# WhatsApp: envío paralelo acotado con detalle por destinatario

Fecha: 2026-08-06

## Qué cambió

- `WhatsAppBackofficeMessageService.sendSelected` envía a Meta con coroutines + `Semaphore` (`whatsapp.backoffice.batch-concurrency`, default 8).
- Cada destinatario sigue siendo un POST individual a Graph API.
- Fallos parciales no abortan el lote; `details[]` cubre todos los `targetIds` en orden.
- `WhatsAppMessageResultDto.clientName` para identificar personas en el panel del backoffice.
- Mensajes amigables para errores Meta `130429`, `131049`, `131056`.

## Config

| Propiedad | Default | Archivos |
|-----------|---------|----------|
| `whatsapp.backoffice.batch-concurrency` | `8` | `application-dev.properties`, `application-prod.properties` |

No es un secreto; solo tuning de concurrencia.

## Pruebas

```bash
./mvnw -Dtest=WhatsAppBackofficeMessageServiceTest test
```
