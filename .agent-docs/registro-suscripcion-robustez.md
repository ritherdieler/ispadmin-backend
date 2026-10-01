# Registro de suscripción: robustez, idempotencia y observabilidad (2026-10-01)

Implementación del plan derivado de la auditoría del alta (Android + Core + observabilidad). Este documento describe el comportamiento vigente; reemplaza a `subscription-registration-async-progress.md` para el alta FIBER.

## Flujo vigente (FIBER)

1. `POST /onu-registration-operations` crea la operación del journal (`provisioning_v2_operation`, id = `operationId`).
   - Por defecto autoriza en la OLT y consulta ACS dentro de la request.
   - Con `PROVISIONING_PREAUTH_ASYNC=true` (y worker activo) responde `PENDING` tras persistir la intención; el loop `ProvisioningPreauthorizationLoop` (cada 3 s) ejecuta OLT + ACS. Las operaciones `RUNNING` sin avance por más de 5 min se reanudan. Android sondea `GET /onu-registration-operations/{id}` cada 3 s mientras `OLT_AUTHORIZATION` esté `PENDING`/`RUNNING`.
2. `POST /subscription` con `registrationOperationId` = `clientRequestId` = `operationId`. Una sola transacción: suscripción, PPPoE, promoción del journal (`FiberRegistrationService.promote`, propagación `MANDATORY`), log `NEW_SUBSCRIPTION`, cierre de la orden de instalación y `SubscriptionRegisteredEvent`.
3. Worker v2 (pool `PROVISIONING_WORKER_THREADS`, lease 300 s) ejecuta MIKROTIK → INTERNET → WIFI → WAN_CLEANUP → VERIFY.
4. Android sondea `GET /subscription/{id}/registration-progress` (2 → 5 s, tolera 3 errores seguidos, tope 5 min).

## Idempotencia y recuperación

| Caso | Comportamiento |
|---|---|
| Reenvío tras respuesta perdida | Mismo `clientRequestId` → 200 `alreadyRegistered=true`. |
| Doble envío concurrente | El perdedor recibe 200 con la suscripción del ganador (`findRegisteredByClientRequestId`), no 409. |
| Resultado incierto en Android (IO/timeout o `REGISTRATION_ALREADY_EXISTS`) | `SubmitAndTrackRegistrationUseCase` consulta `GET /onu-registration-operations/{id}/outcome`; si hay `subscriptionId`, pasa a sondear. Si no, estado `Uncertain` y el reintento manual es seguro. |
| Wireless | `clientRequestId` estable por sesión de formulario (`SavedStateHandle`), se renueva tras un alta exitosa. |
| Reapertura con operación vinculada | Android reanuda el sondeo (antes quedaba colgado) y ofrece «Ver progreso» (`Subscription.Provisioning`). Cancelar advierte que da de baja la suscripción #id. |
| Salir con Atrás durante el envío | Confirmación; el servidor continúa; evento `subscription.register_abandoned_during_submit`. |

`GET /onu-registration-operations/{id}/outcome` → `{operationId, subscriptionId, phase, state, outcome}`; solo el operador dueño.

## Política de reintentos del worker

- Fallo `retryable` → `WAITING` con backoff `min(5 s · 2^(n-1), 5 min)`; tope `MAX_STAGE_ATTEMPTS = 8`; después `FAILED`.
- `WAITING` de una etapa más de 30 min (`STAGE_WAIT_DEADLINE`) → `FAILED` con `STAGE_TIMEOUT` (reintentable a mano). El checkpoint guarda `waitingSinceEpochMs`.
- `FAILED` de una operación vinculada libera el operator lock (el técnico puede iniciar otra alta). Reintento: `POST /subscription/{id}/provisioning/retry`; `POST /subscription/{id}/acs/retry-tr069` delega en el journal cuando hay operación v2.

## Contrato de progreso

`RegistrationProgressDto` agrega `outcome` (`RUNNING`, `WAITING`, `SUCCEEDED`, `FAILED`, `CANCELLING`, `CANCELLED`, `CANCEL_FAILED`) y `operationId`. Con operación v2: `FAILED` → `step=FAILED`, `done=true` y mensaje del fallo; `done` solo en estados terminales. El GET es de solo lectura (sin `refreshTr069FromGateway`) cuando existe operación v2.

## Conflictos HTTP

`POST /subscription` y `/with-facade-photo` responden 409 real para conflictos (`DNI_CONFLICT`, `IP_CONFLICT`, …) manteniendo el cuerpo `BaseResponse`. Android lee `errorCode` en ambos formatos (`RegistrationConflictException`).

## DNI e IP

- Decisión: un cliente puede tener varias suscripciones. `V63` elimina el índice único de `dni` si existe (staging lo tenía; prod no). Aviso no bloqueante en Android con `GET /subscription/dni-check?dni=` (solo conteos).
- IP única entre no canceladas: **no** es migración Flyway. Script manual `scripts/sql/subscription-active-ip-unique.sql`, que aborta si quedan duplicados activos (prod KVM4 tenía 9 filas activas con IP repetida al 2026-10-01).

## Observabilidad

| Señal | Origen | Notas |
|---|---|---|
| `X-Operation-Id` | Android (`ObservabilityHttpInterceptor`, workflow activo) | Core lo pone en MDC `operationId` (`CorrelationIdFilter`) y lo propaga junto a `X-Correlation-Id`/`traceparent` a Gateway y ACS (`OutboundCorrelationInterceptor`). Logback agrega `[op=…]`. |
| Workflow Android `registro_suscripcion` | `startWorkflow(workflowId = operationId)` | Se cierra `SUCCESS`/`FAILED`/`INTERRUPTED`. |
| Eventos `fiber-onboarding` | Outbox del journal | Esperas reintentables = `log`/`warning`; solo `FAILED` terminal = `error`. Tags `stage`, `attempt`, `failureCode`, `retryable`, `phase`. |
| `provisioning.health` | `ProvisioningHealthMonitor` (cada minuto) | Solo con anomalía: operaciones sin avance > 10 min, espera > 15 min, fallos última hora, outbox atrasado > 10 min, worker sin latido > 2 min. Solo conteos. Base para reglas `EVENT_THRESHOLD`. |
| Línea de tiempo | `GET /observability/workflows/{id}/events` | Une eventos de Android y backend por `workflowId`/`correlationId`. Página «Operaciones» en observability-web. |
| Soporte | `GET /onu-registration-operations/admin/operations?state=&olderThanMinutes=` | Panel «Altas en curso o con fallo» en backoffice (`ONUs no configuradas`), con reintento. |

Nunca usar `operationId`/`subscriptionId` como etiqueta de métrica.

### Ruido reducido

`HttpTelemetryPolicy`: no se reporta `http_error` ni se fuerza retención de spans para 4xx en rutas internas (`/api/olt-gateway/`, `/api/acs/`, `/api/traffic/`), ni el despacho `/error`. Causa medida: `cpe/telemetry` interno devolvía 400 ~786 veces/ONU/día. La retención del hub sí funciona (spans 7 d, eventos 30 d).

### Privacidad

- `[HTTP_FAILURE]`: sin cuerpos para `/subscription*` y `/onu-registration-operations*`; resto redactado por patrón (`HttpLogRedactor`); `Authorization` solo con el esquema.
- `networkDevice/coreTypes` ya no expone usuario/contraseña del MikroTik; Android deja de guardarlos en Room. **Rotar la contraseña del core** tras desplegar.
- Android: log HTTP `BODY`, Chucker y Stetho solo en `dev`; staging/prod borran el `chucker.db` heredado al arrancar; sanitización por patrón y recursiva en tags/user/breadcrumbs; replay suprimido en la pantalla de alta (`ObsReplayPrivacy`).

## Hallazgos operativos (2026-10-01)

- `tomcat9027` (prod KVM4) no tenía `PROVISIONING_V2_ENABLED`/`PROVISIONING_V2_WORKER_ENABLED`; resuelto el 2026-10-01 con `/opt/gigafiber/.env.prod-v2` (ver `vps-secrets-management.md`). Prod desplegado el 2026-10-01 con `1.0.4+b186417` (tag `v1.0.4`): V62/V63 aplicadas en `ispadmin`, DNI sin índice único, health 200. `api.gigafiberperu.cloud` aún resuelve al VPS anterior; por eso el prod KVM4 no recibe tráfico de la app (y no hay eventos Android desde 2026-09-19).
- `ensure_face_models_volume` de `deploy.sh` insertaba el montaje `/opt/gigafiber/models` tras la primera entrada de `volumes` en cada deploy (133 líneas duplicadas en `tomcat` y `tomcat-staging`). Corregido el 2026-10-01: ahora analiza el bloque completo del servicio, deja una sola copia y borra las demás. Compose del VPS deduplicado (respaldo `docker-compose.yml.bak-models-dedupe-<timestamp>`), sin reiniciar contenedores.

## E2E staging (2026-10-01)

- Staging con este WAR: V62/V63 aplicadas; la preautorización v2 autorizó ZTEGDC47BFFD y llegó a `READY_FOR_FORM` (ACS respondió). La operación se canceló por API (`CANCELLED` en 15 s) y la ONU volvió a autofind.
- La suscripción 113 («POLAR POPO», que ocupaba ZTEGDC47BFFD) se limpió con `tr069-e2e-hard-cleanup.sh --env staging --id 113 --force`, autorizado por Sergio.
- `FiberRegisterFirstOnuE2ETest` seguía esperando un paso «Serial:» que el asistente de `5faa05a` ya no tiene (autoriza con el primer clic). Se quitó esa espera.
- La etiqueta del campo de lugar cambió de «Lugar» a «Distrito o localidad *»; la prueba ya usa la nueva.
- El e2e completo no llegó al alta. Tras un cold boot del emulador, la mayoría de las corridas siguen quedándose en «Panel / Cargando…» después del login. Staging recibe `/users/login`, `/onu-registration-operations/active` y `/dashboard` (200) y la app no envía nada más: ni la navegación automática al registro ni el clic del menú recomponen la pantalla (el spinner sigue dibujándose). En el volcado JDWP, el hilo principal está ocioso, no hay llamadas OkHttp en curso y la prueba duerme dentro del `runTest` de Compose test 1.9.4. Cambiar las esperas para que avancen frames no lo arregló. Pasa igual en `HEAD` y a mano no se reproduce. 1 de cada ~4 corridas pasa: una autorizó la ONU (`READY_FOR_FORM`) y se detuvo en la etiqueta del lugar, que ya está corregida.
- Alta manual en el emulador (stagingDebug, `android-cli`), 2026-10-01: suscripción **114** «Robusto Prueba», ONU ZTEGDC47BFFD, plan basico, NAP NO-014, PPPoE `gf114`. Preautorización y ACS en ~18 s; `registration-progress` quedó en `outcome=SUCCEEDED`, `tr069ProvisionStatus=COMPLETE`. La limpieza de WAN necesitó 4 intentos (backoff reintentable) y luego cerró sola. La app mostró «¡Registro Exitoso!» con SSID y clave de las dos bandas.
- Staging tenía el hub apagado a propósito desde el 14-sep, así que la telemetría Android respondía 404. Desde el 2026-10-01 está encendido (deploy staging del mismo día): la ingesta con la clave Android responde 202, el outbox de provisión v2 quedó sin pendientes y `obs_event` recibe eventos. Detalle de variables en `vps-secrets-management.md`.
- Pantalla de éxito: la contraseña PPPoE salía «No disponible» porque la suscripción del sondeo (sin secretos) reemplazaba a la respuesta del alta. `toSubscriptionOrNull(registration = …)` ahora conserva `pppoePassword` (TDD en `PollRegistrationProgressUseCaseTest`). El nombre y el plan usan `primary` en lugar de `onPrimaryContainer` sobre la tarjeta gris.
- `RegisterSubscriptionTestTagsTest` y el e2e esperaban la tarjeta de estado OLT/TR-069 que quitó `5faa05a`. Ahora el e2e valida la tarjeta WiFi (`SUCCESS_SECTION_WIFI`) con el SSID y la clave del alta.
- Staging: el hub responde 404 (`gigafiber.subsystems.observability.enabled=false`) y el outbox acumula eventos sin entregar.
- Prod: 0 reglas y 0 canales de alerta; 68 DNI repetidos; duplicado activo 2339/2340 (mismo técnico, plan y NAP, 7 min).
- nginx desplegado usa `proxy_read_timeout 180s` (plantilla alineada).

## Pruebas

Backend: `SubscriptionServiceFiberRegistrationTest`, `ProvisioningRetryPolicyTest`, `SubscriptionControllerRetryTr069V2Test`, `SubscriptionControllerConcurrentDuplicateTest`, `OnuRegistrationOutcomeTest`, `OnuRegistrationAsyncPreauthorizationTest`, `ProvisioningHealthMonitorTest`, `ProvisioningEventRetentionTest`, `HttpLogRedactorTest`, `HttpTelemetryPolicyTest`, `OperationCorrelationTest`, `ObsWorkflowTimelineTest`, `FiberRegistrationServiceTest`.
Android: `SubmitAndTrackRegistrationUseCaseTest`, `PollRegistrationProgressUseCaseTest`, `RegisterSubscriptionComposeViewModelTest` (reanudación, estado de resultado, reconciliación, DNI, workflow, preautorización asíncrona), `ObsSanitizeTest`, `ObservabilityClientSanitizeTest`, `ObsReplayPrivacyTest`, `ObservabilityHttpInterceptorTest`.
Falla previa no relacionada: `RegisterSubscriptionTestTagsTest` espera `OltStatusCard`, que no existe ni en `HEAD`.
