# WhatsApp — Optimización de rendimiento backend (fases 1, 2, 3, 5, 6 y 7)

Implementación de `/Users/sergiocarrillo/.cursor/plans/optimización_rendimiento_whatsapp_0a721245.plan.md`, alcance: fases **1, 2, 3, 5, 6 y 7**. Quedan fuera de este trabajo (a cargo de otro equipo):

- **Fase 4** (envío async del webhook / pipeline de bot en cola separada).
- **Fase 8** (`Payment.subscription` a `FetchType.LAZY`).

Cada fase se implementó con TDD (test que falla primero, verificando con Mockito/MockK cuántas veces se invoca un repositorio, luego implementación mínima para pasarlo) y quedó en un commit independiente.

## Fase 1 — Índices de BD + tuning Hibernate/HikariCP

### Índices nuevos (`db/migration/V14__whatsapp_performance_indexes.sql` + anotaciones `@Index` en las entidades)

| Índice | Tabla / entidad | Columnas |
|---|---|---|
| `idx_wa_message_log_subscription_type_created` | `WhatsAppMessageLog` | `subscription_id, message_type, created_at` |
| `idx_wa_message_log_payment_type_created` | `WhatsAppMessageLog` | `payment_id, message_type, created_at` |
| `idx_wa_message_log_campaign_id` | `WhatsAppMessageLog` | `campaign_id` |
| `idx_wa_message_log_type_created` | `WhatsAppMessageLog` | `message_type, created_at` |
| `idx_wa_inbound_phone_read_at` | `WhatsAppInboundMessage` | `phone, read_at` |
| `idx_wa_audit_action_created` | `WhatsAppAuditLog` | `action, created_at` |

Se mantiene el patrón ya existente en el proyecto de declarar el índice tanto en la migración Flyway como en la anotación `@Table(indexes = [...])` de la entidad (Hibernate con `ddl-auto=update` no duplica si el nombre coincide).

### Tuning (`application-prod.properties`, `application-dev.properties`)

```properties
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order-inserts=true
spring.jpa.properties.hibernate.order-updates=true
spring.jpa.properties.hibernate.default-batch-fetch-size=25
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
```

### Tests nuevos

- `WhatsAppPerformanceIndexesTest`: verifica por reflexión que las tres entidades declaran los nombres de índice esperados.
- `WhatsAppPerformancePropertiesTest`: carga los `.properties` de prod/dev y verifica los valores de batch/pool.

## Fase 2 — N+1 en analytics de conversión (`WhatsAppAnalyticsService`)

**Problema:** `conversion()` y `buildCampaignAnalytics()` llamaban `paymentRepository.findBySubscriptionIdOrderByBillingDateDatetimeDesc(subscriptionId)` **una vez por cada log** (`recoveredForLog`, `hasPaidInWindow`), generando N consultas para N mensajes de la misma suscripción.

**Fix:**

- Nuevo método batch en `PaymentRepository`: `findBySubscriptionIdInAndPaidTrueAndPaymentDateDatetimeBetween(subscriptionIds, from, to)`.
- Nuevo helper `loadPaymentsBySubscription(logs, windowDays)`: calcula el rango de fechas cubriendo todos los logs, ejecuta **una sola query** con todos los `subscriptionId` presentes y arma un `Map<Int, List<Payment>>`.
- `recoveredForLog()` y `hasPaidInWindow()` ahora reciben ese mapa en vez de llamar al repositorio.
- `overview()` ya no vuelve a llamar `messageLogRepository.findByCreatedAtBetween` dentro de `conversion()`: se agregó `conversionFromLogs()` que reutiliza los logs ya cargados por `overview()`.
- `campaigns()`/`campaignDetail()` cargan los pagos una sola vez para todas las campañas del rango, no por campaña.

### Tests nuevos (`WhatsAppAnalyticsServiceTest`)

- `conversion consulta pagos en una sola query batch para varios logs de la misma suscripcion`
- `conversion calcula recoveredAmount a partir de los pagos cargados en batch`
- `campaigns consulta pagos en una sola query batch para todas las campanas`
- `overview no vuelve a consultar messageLogRepository dentro de conversion`

## Fase 3 — N+1 en candidatos de recordatorio + `reminder` sin límite (`WhatsAppBackofficeMessageService`)

**Problemas:**

1. `listCandidates` para `PAYMENT_REMINDER` usaba `findAllReminderCandidatePayments()`, que traía **todas** las filas sin `LIMIT` ni filtro de teléfono en SQL.
2. `partitionPaymentEntities`/`partitionPaymentRows` llamaban `whatsAppMessageLogRepository.hasSentToday(...)` **una vez por candidato** para saber si ya se envió el mensaje hoy.

**Fix:**

- `PaymentRepository.findReminderCandidatePaymentRows(limit)`: reemplaza a `findAllReminderCandidatePayments()`, agrega `LIMIT` (alineado al override del límite diario, con default `DEFAULT_REMINDER_CANDIDATE_LIMIT = 2000`).
- `WhatsAppMessageLogRepository.findPaymentIdsSentToday(...)` / `findSubscriptionIdsSentToday(...)`: devuelven el **set de ids ya enviados hoy** en una sola query, en vez de N.
- `partitionPaymentRows` ahora calcula ese set una sola vez antes de iterar y solo hace `.contains()` por fila.
- Se eliminó `partitionPaymentEntities` (quedó sin uso tras el cambio).

### Tests nuevos (`WhatsAppBackofficeMessageServiceTest`)

- `listCandidates for reminder aplica limite alineado al daily-limit configurado`
- `listCandidates for reminder consulta hasSentToday en una sola query batch para N candidatos`

## Fase 5 — N+1 en bandeja de conversaciones y contexto (`WhatsAppConversationQueryService`)

**Problemas:**

1. `listConversations()` (sin filtro de fecha) traía `findTop500ByOrderByCreatedAtDesc` de inbound y outbound por separado y agrupaba en memoria — el límite de 500 mensajes (no conversaciones) podía dejar fuera conversaciones completas si había mucho volumen reciente concentrado en pocos teléfonos.
2. `getContext()` llamaba, **por cada variante de teléfono** del cliente: `findTop1ByPhoneOrderByCreatedAtDesc`, `getServiceWindow(phone)` y `findTop10ByPhoneOrderByCreatedAtDesc`; además cargaba **todos** los pagos de la suscripción para luego truncar a 5 en memoria.

**Fix:**

- Nuevo `WhatsAppInboundMessageRepository.findRecentActivePhones(limit)`: query nativa (`UNION`) que rankea teléfonos por actividad reciente combinando inbound y outbound, acotada a `RECENT_PHONE_RANK_LIMIT = 500` conversaciones (no mensajes).
- `findByPhoneIn(phones)` en ambos repositorios (inbound y `WhatsAppMessageLogRepository`) para traer los mensajes de los teléfonos ya rankeados en 2 queries en total, en vez de N.
- El camino con filtro de fecha explícito (`dateFrom`/`dateTo`) se deja igual (`findByCreatedAtBetween`), fuera de alcance de este fix puntual.
- `getContext()`: `findTop1ByPhoneInOrderByCreatedAtDesc(phones)`, `serviceWindowService.getServiceWindows(phones)` (batch ya existente, solo se cambió el call-site) y `findByPhoneInOrderByCreatedAtDesc(phones, pageable)` reemplazan los N llamados por variante.
- Nuevo `PaymentRepository.findTop5BySubscriptionIdOrderByBillingDateDatetimeDesc(subscriptionId)`: aplica `LIMIT 5` en SQL en vez de traer todo y hacer `.take(5)`.

### Tests nuevos/actualizados (`WhatsAppConversationQueryServiceTest`)

- `listConversations groups by phone with unread and last preview` (actualizado): verifica uso de `findRecentActivePhones` + `findByPhoneIn`, y que **no** se llama a `findTop500ByOrderByCreatedAtDesc`.
- `listConversations con rango de fechas sigue usando findByCreatedAtBetween sin rankear telefonos` (nuevo).
- `getContext returns subscription debt and recent logs` (actualizado): verifica las nuevas llamadas batch.

## Fase 6 — Batch en `markAllRead` y conteo de no leídos

**Problemas:**

1. `WhatsAppConversationService.markAllRead()` iteraba cada mensaje no leído y hacía: 1 llamada a `whatsAppService.markMessageAsRead()` (API de Meta) + 1 `save()` por mensaje.
2. `WhatsAppInboundMessageService` calculaba el conteo de no leídos con `.findByPhoneAndReadAtIsNull(phone).size`, cargando todas las filas solo para contarlas.

**Fix:**

- `WhatsAppInboundMessageRepository.markReadByIds(ids, readAt)`: `@Modifying` `UPDATE` batch para marcar `readAt` de todos los mensajes no leídos de una vez.
- `markAllRead()` ahora llama a la API de Meta **una sola vez**, sobre el mensaje no leído más reciente (WhatsApp marca toda la conversación como leída con un solo ACK), y luego hace un único `UPDATE` batch para persistir `readAt` en todos los mensajes.
- `WhatsAppInboundMessageRepository.countByPhoneAndReadAtIsNull(phone): Long`: reemplaza a `findByPhoneAndReadAtIsNull(phone).size` en `WhatsAppInboundMessageService`.

### Tests nuevos/actualizados

- `WhatsAppConversationServiceTest`: `markAllRead persists readAt for all unread inbound of phone` (actualizado a verificar `markReadByIds`), `markAllRead llama markMessageAsRead una sola vez para el mensaje mas reciente` (nuevo), `markAllRead no hace nada si no hay mensajes no leidos` (nuevo).
- `WhatsAppInboundMessageServiceTest`: stubs migrados a `countByPhoneAndReadAtIsNull`.

## Fase 7 — Hallazgos menores de N+1

- `WhatsAppBackofficeMessageService.listTemplates()`: usaba `syncedTemplateRepository.findByName(name)` por cada plantilla configurada. Ahora usa `findByNameIn(names)` en una sola query y arma un mapa por nombre.
- `WhatsAppMetaAnalyticsParser.parseTemplateAnalytics()`: resolvía el nombre de cada plantilla con `templateRepository.findById(id)` dentro del loop de agregación. Ahora usa `findAllById(ids)` una sola vez.
- `WhatsAppBackofficeController.analyticsCampaignDetail`: volvía a consultar `messageLogRepository.findByCampaignId(campaignId)` para armar el DTO de mensajes, a pesar de que `WhatsAppAnalyticsService.campaignDetail()` ya había cargado esos logs. Se agregó el campo `logs: List<WhatsAppMessageLog>` a `WhatsAppCampaignDetail` para que el controller reutilice esa lista sin repetir la query (el controller sigue devolviendo el DTO correspondiente, no la entidad).
- `WhatsAppAnalyticsService.campaigns()`/`campaignDetail()`: el cálculo de `responded` (mensajes inbound de respuesta por campaña) escaneaba la lista completa de inbound del rango **por cada campaña**. Ahora se indexa `inbound` por teléfono una sola vez (`Map<String, List<WhatsAppInboundMessage>>`) y se reutiliza para todas las campañas del mismo `campaigns()`/`campaignDetail()`.

### Tests nuevos/actualizados

- `WhatsAppBackofficeMessageServiceTest`: `listTemplates consulta plantillas sincronizadas en una sola query batch` (nuevo).
- `WhatsAppMetaAnalyticsParserTest`: ambos tests existentes actualizados para stubear `findAllById` y verificar que `findById` no se usa.
- `WhatsAppAnalyticsServiceTest`: `campaignDetail returns summary and messages for a campaign` (actualizado, asserts sobre `detail.logs`), `campaigns calcula responded por telefono indexando inbound una sola vez para todas las campanas` (nuevo).

## Archivos tocados

**Producción:**

- `data/model/WhatsAppMessageLog.kt`, `WhatsAppInboundMessage.kt`, `WhatsAppAuditLog.kt`
- `db/migration/V14__whatsapp_performance_indexes.sql` (nuevo)
- `application-prod.properties`, `application-dev.properties`
- `repository/PaymentRepository.kt`
- `repository/WhatsAppMessageLogRepository.kt`
- `repository/WhatsAppInboundMessageRepository.kt`
- `repository/WhatsAppSyncedTemplateRepository.kt`
- `service/whatsapp/WhatsAppAnalyticsService.kt`
- `service/WhatsAppBackofficeMessageService.kt`
- `service/whatsapp/WhatsAppConversationQueryService.kt`
- `service/whatsapp/WhatsAppConversationService.kt`
- `service/WhatsAppInboundMessageService.kt`
- `service/whatsapp/WhatsAppMetaAnalyticsParser.kt`
- `controller/WhatsAppBackofficeController.kt`

**Tests (nuevos o actualizados):**

- `data/model/WhatsAppPerformanceIndexesTest.kt` (nuevo)
- `config/WhatsAppPerformancePropertiesTest.kt` (nuevo)
- `service/whatsapp/WhatsAppAnalyticsServiceTest.kt`
- `service/WhatsAppBackofficeMessageServiceTest.kt`
- `service/whatsapp/WhatsAppConversationQueryServiceTest.kt`
- `service/whatsapp/WhatsAppConversationServiceTest.kt`
- `service/WhatsAppInboundMessageServiceTest.kt`
- `service/whatsapp/WhatsAppMetaAnalyticsParserTest.kt`

No se tocaron `WhatsAppWebhookService.kt` (fase 4, envío async, fuera de alcance) ni `Payment.kt`/`Subscription.kt` para el `FetchType` de la relación `subscription` (fase 8, fuera de alcance).

## Resultado de pruebas

- Suite completa: **865 tests**. Con todas las fases aplicadas, la única diferencia frente al baseline previo al trabajo son los tests nuevos agregados en cada fase.
- Fallas observadas en la corrida de la suite completa (16 failures + 2 errors): **todas preexistentes y no relacionadas** con estos cambios — `OltInventorySyncServiceTest` / `OltInventorySyncServiceProxySafetyTest` (flakiness por estado compartido/orden de ejecución entre tests del módulo OLT, confirmado porque pasan en verde al ejecutarse en aislamiento), `HuaweiCliSessionReadTest.readUntil falla rapido si la sesion SSH muere durante el comando` y `CrmConversationServiceTest.markPendingOnHandoff creates or updates conversation` (fallas base ya presentes antes de iniciar este trabajo).
- Todos los tests de WhatsApp (analytics, backoffice message service, conversation query/service, inbound message service, meta analytics parser, índices/propiedades de performance) pasan en verde tanto de forma aislada como dentro de la suite completa.

## Commits

| Fase | Hash | Mensaje |
|---|---|---|
| 1 | `e6eeb64` | `perf(whatsapp): indices de BD y tuning de Hibernate/HikariCP (fase 1)` |
| 2 | `5c0a4cf` | `perf(whatsapp): eliminar N+1 en analytics con batch de pagos por suscripcion (fase 2)` |
| 3 | `6065d5a` | `perf(whatsapp): eliminar N+1 en candidatos de recordatorio y hasSentToday batch (fase 3)` |
| 5 | `4464f85` | `perf(whatsapp): eliminar N+1 en bandeja de conversaciones y contexto (fase 5)` |
| 6 | `4fb8c4b` | `perf(whatsapp): markAllRead con UPDATE batch y conteo de no leidos sin cargar filas (fase 6)` |
| 7 | `c13e719` | `perf(whatsapp): eliminar N+1 en plantillas, analytics de Meta y detalle de campana (fase 7)` |

Rama de trabajo: `perf/whatsapp-fases-1-2-3-5-6-7`.

## Fuera de alcance (a cargo de otro equipo)

- **Fase 4** — Envío async del webhook (persistir inbound y responder rápido, procesar pipeline de bot/LLM/media en cola separada con timeout). Se evaluó un scope reducido (timeout acotado dentro de la corrutina ya asíncrona de `processPayloadAsync`) pero se descartó por el riesgo de romper la cobertura de tests existente que asume ejecución síncrona de `processInboundMessage`; se deja íntegramente para el otro equipo.
- **Fase 8** — Cambiar `Payment.subscription` a `FetchType.LAZY`. No se tocó `Payment.kt` ni `Subscription.kt`.
