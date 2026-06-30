# Recordatorios WhatsApp

Guia operativa para probar y usar los recordatorios de pago por WhatsApp en el backend de WispAdmin.

## Configuracion local

El token de WhatsApp no debe guardarse en Git. Para desarrollo local debe estar en:

```text
src/main/resources/application-local.properties
```

Con este formato:

```properties
whatsapp.access-token=TOKEN_DE_META
```

Este archivo esta ignorado por Git mediante `.gitignore`.

El profile local debe estar activo junto con dev:

```properties
spring.profiles.active=dev,local
```

## Modo de envio

La configuracion actual recomendada es modo texto:

```properties
whatsapp.payment-reminder-mode=text
```

El modo plantilla queda preparado para una etapa posterior:

```properties
whatsapp.payment-reminder-mode=template
```

No activar `template` hasta que la plantilla de Meta este aprobada y probada correctamente.

## Endpoints de prueba

### Probar token y envio simple

```http
POST /ispadmin/whatsapp/test-message
```

Body:

```json
{
  "phoneNumber": "902354183",
  "message": "Prueba de envio desde backend GigaFiber"
}
```

Respuesta esperada:

```text
Mensaje enviado correctamente por WhatsApp.
```

### Probar plantilla

```http
POST /ispadmin/whatsapp/test-template-message
```

Body:

```json
{
  "phoneNumber": "902354183",
  "clientName": "Edwin",
  "amount": "50",
  "billingPeriod": "del 1 al 10 de cada mes"
}
```

Nota: este endpoint depende de que Meta tenga aprobada la plantilla configurada.

## Recordatorios de pago

### Envio individual

```http
POST /ispadmin/payment/{paymentId}/send-whatsapp-reminder
```

Ejemplo:

```http
POST /ispadmin/payment/474/send-whatsapp-reminder
```

Respuestas esperadas:

```text
200 OK  - Recordatorio enviado correctamente.
409     - Ya se envio un recordatorio hoy.
422     - Numero no autorizado en Meta para pruebas.
401     - Token de WhatsApp invalido o vencido.
400     - Telefono invalido o plantilla no disponible.
500     - Error inesperado.
```

### Envio masivo

```http
POST /ispadmin/payment/send-whatsapp-reminders?limit=3
```

El backend toma hasta `limit` pagos pendientes aptos para WhatsApp.

Criterios usados:

- Pago pendiente.
- Cliente con telefono registrado.
- Telefono con formato compatible con celular peruano.
- Sin recordatorio `SENT` registrado hoy.
- Ordenado por fecha de facturacion e id.

El lote no se detiene si un envio falla. Cada intento se informa en `details`.

### Envio mensual manual

```http
POST /ispadmin/payment/send-monthly-whatsapp-reminders?limit=5
```

Este endpoint permite probar manualmente el proceso mensual sin esperar al dia 1.

Reglas principales:

- Toma facturas pendientes del mes anterior.
- Permite `limit` opcional para pruebas controladas.
- Omite facturas que ya tuvieron `SENT` durante el mes actual.
- Omite clientes sin telefono.
- Omite telefonos que no son celulares peruanos validos.
- Registra errores de WhatsApp como `FAILED`.

Sin `limit`, procesa todas las facturas pendientes del mes anterior:

```http
POST /ispadmin/payment/send-monthly-whatsapp-reminders
```

## Scheduler mensual

El backend ejecuta automaticamente el proceso mensual:

```text
Dia 1 de cada mes a las 9:00 AM, hora Peru
```

Configuracion:

```kotlin
@Scheduled(cron = "0 0 9 1 * *", zone = "America/Lima")
```

El scheduler llama al proceso mensual sin limite. Para pruebas manuales se recomienda usar el endpoint con `limit`.

## Estados

```text
SENT     Recordatorio enviado correctamente.
FAILED   Se intento enviar, pero WhatsApp/Meta rechazo el envio.
SKIPPED  No se intento enviar porque habia una regla de negocio que lo impedia.
```

Ejemplos de `SKIPPED`:

- Ya se envio recordatorio hoy.
- El pago no tiene cliente asociado.
- El cliente no tiene telefono registrado.

## Auditoria

### Ultimos logs

```http
GET /ispadmin/whatsapp/logs
```

### Logs por pago

```http
GET /ispadmin/whatsapp/logs/payment/{paymentId}
```

Ejemplo:

```http
GET /ispadmin/whatsapp/logs/payment/4
```

La respuesta incluye:

```text
id
paymentId
subscriptionId
phone
messageType
status
errorMessage
createdAt
```

No se expone el cuerpo completo del mensaje desde estos endpoints.

## Pendiente para plantillas Meta

Cuando la plantilla este aprobada:

1. Probar `POST /ispadmin/whatsapp/test-template-message`.
2. Confirmar que el nombre e idioma coincidan:

```properties
whatsapp.payment-reminder-template-name=payment_reminder_gigaperu
whatsapp.payment-reminder-template-language=es_PE
```

3. Si Meta exige variables nombradas, adaptar el payload del backend antes de activar `template`.
4. Cambiar:

```properties
whatsapp.payment-reminder-mode=template
```

5. Reiniciar backend.
6. Probar envio individual y envio masivo.

## Verificaciones antes de commit

```powershell
git diff | Select-String "EAA"
git status --short --ignored src/main/resources/application-local.properties
.\mvnw -q compile
```

Resultados esperados:

- No debe aparecer ningun token en el diff.
- `application-local.properties` debe aparecer como ignorado con `!!`.
- La compilacion debe finalizar sin errores.
