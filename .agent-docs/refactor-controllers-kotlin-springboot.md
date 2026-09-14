# Refactor de Controllers (Kotlin + Spring Boot)

Documento del refactor de los controllers de dominio en
`src/main/kotlin/com/dscorp/wispadmin/wispadmin/controller/`, siguiendo el skill
`kotlin-springboot` y usando como plantilla el piloto ya hecho de `CouponController`
(`controller/CouponController.kt`, `service/CouponService.kt`, `dto/CouponDto.kt`).

## Contexto

- `spring-boot-starter-validation` ya está en el `pom.xml`.
- `wispadmin/logging/GlobalExceptionHandler.kt` captura **todas** las excepciones,
  las registra (archivos de log + tabla `ErrorLog`) y las reporta a observabilidad.
  También maneja `MethodArgumentNotValidException` (respuestas 400 de validación).
- Por eso el refactor elimina los `try/catch { e.printStackTrace() }` y deja que las
  excepciones se propaguen al handler global.

## Patrón aplicado

### Nivel 1 — mecánico (siempre seguro)

Aplicado a todos los controllers de dominio:

- Inyección por constructor (`private val ...`) en lugar de `@Autowired lateinit var`
  / `@Autowired constructor`.
- Eliminación de los campos "constantes" de error
  (`objectErrorResponse` / `listObjectErrorResponse` / etc.).
- Eliminación de los `try/catch` + `printStackTrace` + `errorLogRepository.save(...)`
  genéricos; las excepciones se propagan al `GlobalExceptionHandler`.
- Uso de `ResponseEntity.ok(...)`, `ResponseEntity.notFound().build()`,
  `ResponseEntity.badRequest()...` en vez de `ResponseEntity.status(200/404/500)`.
- Logger en `companion object` con `LoggerFactory` y logs parametrizados
  (donde el controller realmente necesita loguear).
- **No** se cambiaron rutas, métodos HTTP ni la forma del JSON de entrada/salida.

Se **preservaron** los `catch` que mapean excepciones a códigos HTTP con significado
de negocio (400/401/403/404/409/422), porque forman parte del contrato del endpoint.
Solo se eliminaron las ramas genéricas que devolvían 500.

### Nivel 2 — DTOs + capa `@Service` + `@Valid`

Aplicado **solo** donde el controller recibía/devolvía la **entidad JPA directamente**
y el `ResponseDto` podía reflejar exactamente los campos serializados sin cambiar el JSON
(igual que en `Coupon`). Se usa `@field:` en las validaciones y
`@JsonIgnoreProperties(ignoreUnknown = true)` en los request DTO.

## Controllers migrados a Nivel 2

| Controller | DTOs / Service creados | Notas |
| --- | --- | --- |
| `AppVersionController` | `dto/AppVersionDto.kt` (`AppVersionResponseDto` + `toResponseDto`), `service/AppVersionService.kt` | GET `/app/check_version`. Antes devolvía la entidad `AppVersion`; el `ResponseDto` refleja exactamente sus campos. Si no hay versión, devuelve `404` (antes: cualquier excepción devolvía 404). |
| `FcmController` | `dto/FcmTokenDto.kt` (`FcmTokenRequestDto` + `FcmTokenResponseDto` + mappers), `service/FcmTokenService.kt` | `save-token` y `get-token`. Antes recibía/devolvía la entidad `FcmToken` (2 campos). Se agregó `@Valid` en `save-token`. El endpoint `sendNotification/{token}` se mantuvo igual en el controller (no es CRUD de entidad). |

`CouponController` ya era el piloto Nivel 2 (no se modificó).

## Controllers migrados a Nivel 1

- `PlanController`
- `PaymentController`
- `UserController`
- `SubscriptionController`
- `TechnicianController`
- `NapBoxController`
- `MufaController`
- `NetworkDeviceController`
- `NetworkDeviceConnectionController`
- `IpPoolController`
- `InstallationOrderController`
- `FixedCostController`
- `FilterRuleController`
- `OutLayController`
- `PlaceController`
- `ReportController`
- `ScheduledTaskLogController`
- `DashBoardController`
- `AssistanceTicketController`
- `OnuController`
- `AppManagementController` (solo se quitó `@Autowired` redundante del constructor)
- `MockOltDebugController` (solo se quitó `@Autowired` redundante del constructor)
- `LogViewerController` (solo se quitó `@Autowired` redundante del constructor)

## Controllers que quedaron SOLO en Nivel 1 por riesgo de contrato

Estos siguen recibiendo/devolviendo entidades JPA o estructuras con relaciones/lazy,
o tienen lógica de negocio pesada. Convertirlos a DTOs podría cambiar el JSON o el
comportamiento; **quedan pendientes de coordinación con clientes**:

- **`PlanController`**: los endpoints devuelven la entidad `Plan` completa
  (`GET /plan`, `POST`, `PUT`, `activate/deactivate`) y `PUT /plan` ejecuta comandos
  Mikrotik en un hilo separado (`CompletableFuture.runAsync`). Se mantuvo esa ejecución
  asíncrona **sin cambios** dentro del controller para no alterar el comportamiento.
- **`SubscriptionController`**: devuelve `SubscriptionDto`/`Coupon`/`BaseResponse` con
  campos calculados, documentos Excel/HTML y relaciones; muchos endpoints con lógica
  específica. Solo Nivel 1.
- **`PaymentController`**: usa `PaymentDto`/`BaseResponse` y mapeos no triviales; los
  endpoints de WhatsApp mapean mensajes de error a códigos HTTP específicos
  (409/401/400/422). Se preservaron esas ramas.
- **`UserController`**: recibe la entidad `User` y devuelve `UserDto` (mapper existente).
  Se preservó el `409` ante `DataIntegrityViolationException` y el swallow controlado
  de errores al enviar la notificación de force-logout.
- **`AssistanceTicketController`, `NetworkDeviceController`, `MufaController`,
  `PlaceController`, `IpPoolController`, `NapBoxController`, `TechnicianController`,
  `InstallationOrderController`, `OutLayController`, `FixedCostController`,
  `DashBoardController`, `ReportController`**: ya devolvían DTOs o usan servicios de
  dominio; el lado request en algunos casos recibe la entidad JPA (p. ej. `Mufa`,
  `Place`, `NetworkDeviceRequest`, `User`). No se forzaron request DTOs para no arriesgar
  el descarte de campos que hoy envían los clientes.

## Smart Map (actualización 2026-07-22)

`SmartMapController` y `SmartMapIntelligenceController` se refactorizaron a
Nivel 2. Detalle en `smart-map-kotlin-springboot-refactor.md`.

## Controllers NO tocados (fuera de alcance)

- Observabilidad (`observability/controller/*`): refactorizados por otro agente.
- `controller/izipay/*` (`HealthResource.java`, `CreateResource.kt`,
  `VerifyResultResource.kt`): integración de pasarela de pago / archivo Java.
- WebSocket (`InterfaceTrafficWebSocket`, `DeviceResourcesWebSocket`).
- `MainController`, `PerformanceController`, `WhatsAppController`,
  `WhatsAppWebhookController`, `AttendanceController`, `FaceVerifyController`,
  `FaceEvidenceController`, `FaceDataController`: ya eran idiomáticos
  (inyección por constructor, sin `printStackTrace` ni constantes de error, `try/catch`
  que devuelven respuestas degradadas con significado). No requerían cambios.
- `AttendanceLogController.kt`: archivo completamente comentado.

## Cambios de comportamiento relevantes

- **Errores 500**: donde antes se devolvía `ResponseEntity.status(500).body(null)`
  (cuerpo nulo) o un `BaseResponse(status=500, ...)` con HTTP 200, ahora las excepciones
  se propagan al `GlobalExceptionHandler`, que responde con HTTP 500 y un JSON
  `{ timestamp, status, error, path }`. El registro de errores (log + `ErrorLog` +
  observabilidad) lo sigue haciendo el handler global, por lo que no se pierde trazabilidad.
- **Validación (400)**: `FcmController.save-token` ahora valida con `@Valid`; un
  `subscriptionId`/`token` faltante devuelve **400** (via `MethodArgumentNotValidException`)
  en lugar de fallar más adelante. Igual criterio que el piloto `CouponController`.
- **`AppVersionController.check_version`**: `404` solo cuando no existe ninguna versión
  (antes: cualquier excepción devolvía 404).
- Se preservaron todos los códigos de negocio existentes: 404 (no encontrado),
  409 (conflicto/duplicado), 401/403 (auth facial/credenciales), 400/422 (validaciones
  y errores de WhatsApp), 201 (creación), 204 (eliminación de ticket pendiente).

## Compilación

`sh mvnw -q -DskipTests compile` → **BUILD SUCCESS** (validado de forma incremental por
lotes de controllers).

## Recordatorio de despliegue

El backend de desarrollo (puerto 8080) debe **reiniciarse** para tomar los cambios
(nuevos beans `@Service`, DTOs con validación y dependencias/propiedades).
