# Auditoría de subsistemas — refactor por fases (2026-09-01)

Ejecución del plan `auditoria_subsistemas_backend`. Una sección por fase, con lo que cambió,
las pruebas que lo cubren y lo que hay que vigilar en producción.

Contexto que ordenó las prioridades: la base son 10.030 MB, de los que netdiag es el 58,1% y
observability el 33,1%. El núcleo transaccional del ISP (1334 suscripciones, 813 ONUs) cabe en 67 MB.

---

## F1 — Timeouts del cliente SmartOLT y secretos fuera de git

**Problema.** `HttpClient` era un `object` estático: creaba un `RestTemplate` por llamada, ignoraba
los `@Value` de `RealOltService` y no tenía timeouts (picos medidos de 367 s). La API key de SmartOLT
y la contraseña de MySQL de producción estaban literales en el repo.

**Cambios.**

- `HttpClient.kt` y `ConfigConstants.kt` eliminados.
- `SmartOltHttpClient` (componente inyectable) + `SmartOltHttpConfig` (bean `smartOltRestTemplate`
  con connect/read timeout) + `OltServiceProperties` (`olt.service.*`).
- `application-prod.properties` y `application-dev.properties` leen la key y la contraseña del entorno.

**Variables nuevas** (catalogadas en `vps-secrets-management.md`): `OLT_SERVICE_API_KEY`,
`OLT_SERVICE_BASE_URL`, `OLT_SERVICE_CONNECT_TIMEOUT_MS`, `OLT_SERVICE_READ_TIMEOUT_MS`, `DB_PASSWORD`.

**Pendiente operativo.** Rotar la API key de SmartOLT en el panel: estuvo en git.

**Pruebas.** `SmartOltHttpClientTest` (MockWebServer: URL, header `X-Token`, corte por read timeout),
`OltServiceSecretsPropertiesFileTest` (guarda que los properties no lleven valores literales).

---

## F2 — Dieta de netdiag

**Problema.** `net_diag_alert_decision` (2.259 MB, 3,08M filas) y `net_diag_incident_event` (558 MB)
guardaban **exactamente las mismas 3.080.038 filas**. El 87,3% eran decisiones `SUPPRESSED`.
`net_diag_olt_log_event` tenía 2,7M filas y **solo el índice PRIMARY**. Nada de esto se purgaba.

**Cambios.**

- `NetDiagIncidentEvent` absorbe `reason_code` y `target_id`; `NetDiagAlertDecision` deja de escribirse
  y queda como tabla histórica.
- `NetDiagAlertSuppressionWindow`: las supresiones se agregan por ventana horaria
  (`incident_id, target_id, reason_code, window_start`) con `incrementWindow` atómico.
- Índices nuevos en `net_diag_olt_log_event`, `net_diag_incident_event` y `net_diag_notification_log`.
- `NetDiagRetentionService` + `NetDiagRetentionWriter`: purga por lotes, cada lote en su propia
  transacción (`REQUIRES_NEW`), programada a las 03:45.
- El resumen de incidentes pasa de tres `count(*)` a una consulta agrupada, cacheada con TTL
  (`NetDiagIncidentSummaryCache`).

**Migración.** `V39__netdiag_retention_and_indexes.sql`, idempotente (procedures
`netdiag_add_column_if_missing` / `netdiag_add_index_if_missing`) porque hasta F9 sigue vivo `ddl-auto=update`.

**Retención configurable.** `net.diag.retention.*`: `olt-log-event-days=14`, `incident-event-days=90`,
`alert-decision-days=7`, `notification-log-days=90`, `suppression-days=30`, `batch-size`, `max-batches-per-run`.

---

## F3 — Observabilidad: índices, muestreo y truncado

**Problema.** `obs_span` cargaba índices de una sola columna redundantes con las consultas reales,
`tracing.sample-rate` estaba declarado pero **no se usaba** (se guardaba el 100% de los spans SERVER,
368k en 7 días) y los eventos llegaban con ~13,7 KB de breadcrumbs sin recortar.

**Cambios.**

- `ObsSpan`: `idx_obs_span_trace` e `idx_obs_span_parent` se sustituyen por
  `idx_obs_span_trace_parent (trace_id, parent_span_id)` y `idx_obs_span_root_start (parent_span_id, start_epoch_ms)`.
  `http_route` es `VARCHAR(500)` y no cabe en un índice: se indexa por prefijo solo en la migración
  (`idx_obs_span_route (http_route(96), start_epoch_ms)`).
- `TraceContextFilter` implementa el muestreo. Fuera de la muestra **solo** se conservan los spans
  SERVER lentos (`always-sample-above-ms`) o con error (HTTP >= 400). La decisión viaja en
  `TraceScope.sampled` y en el `traceparent` de respuesta; `ObsTracer` no encola spans hijos de trazas
  no muestreadas, así que una traza guardada nunca queda a medias.
  Si el `traceparent` entrante dice `sampled=0` se respeta; en el resto se decide localmente, porque
  los SDK web mandan siempre `01` y honrarlos dejaría el muestreo sin efecto.
- `ObsIngestionService` recorta en la ingesta: últimos N breadcrumbs, y si el JSON de breadcrumbs o de
  contexto pasa del límite se sustituye por `{"truncated":true,"originalChars":N}` (JSON válido, no un corte a medias).
- SDK web compartido (`ispadmin-backoffice` y `ispadmin-asistencias`): el buffer baja de 40 a 20
  breadcrumbs, trunca el mensaje a 300 caracteres y descarta el `data` que pase de 1000.

**Migración.** `V40__obs_span_indexes.sql`, idempotente.

**Configuración nueva.** `observability.tracing.always-sample-above-ms=1000`,
`observability.ingest.max-breadcrumbs=20`, `max-breadcrumbs-chars`, `max-context-chars`,
`max-message-chars`, `max-stacktrace-chars`. En producción `observability.tracing.sample-rate` pasa a
`${OBS_TRACING_SAMPLE_RATE:0.2}`.

**Pruebas.** `TraceContextFilterSamplingTest` (9), `ObsSpanIndexesTest` (3),
`ObsIngestionTruncationTest` (5), `breadcrumbs.test.ts` en backoffice (6).

**Qué vigilar.** Con `sample-rate=0.2` el waterfall completo existe solo para 1 de cada 5 peticiones.
Si hace falta depurar un endpoint concreto, subir la tasa temporalmente por variable de entorno.

---

## F4 — Bus CLI de dos carriles

**Problema.** `OltCliBus` tenía una única sesión SSH y una única cola. El volcado de alarmas
(300 s) y el sync de inventario bloqueaban al técnico en campo.

**Cuota acordada.** La OLT admite 3 sesiones simultáneas. El backend usa **2** y deja la 3.ª libre
para un operador humano o para `scripts/backup_olt_to_gdrive.sh`.

**Carriles.**

| Carril | Tipos de job | Sesión |
|--------|--------------|--------|
| `INTERACTIVE` | `WRITE`, `ADHOC` | 1 |
| `BACKGROUND` | `INVENTORY`, `SIGNAL_POLL`, `ALARM_POLL`, `KEEPALIVE` | 1 |

**Cambios.**

- Cada carril tiene su propia sesión, cola de prioridad, lock y hilo trabajador.
- El `ThreadLocal<Boolean>` pasa a `ThreadLocal<HuaweiCliSession>`: un `submit` anidado se resuelve
  **contra la sesión del carril en curso**, nunca salta al otro. Sin esto habría bloqueo mutuo y
  corrupción de modo CLI (un job dentro de `interface gpon 0/1` ejecutando comandos en otra sesión).
- La deduplicación (`already_running` / `already_queued`) es por carril.
- Keepalive por sesión: el tick late en cada carril ocioso por separado.
- `sessionCount()` devuelve los carriles activos. `queueDepth()` y `busyJobType()` siguen dando el
  agregado para el estado que ya consumen los servicios de sync, y admiten un `CliLane` para el detalle.
- `OltReachabilityTracker` sigue **compartido**: los dos carriles hablan con la misma OLT por el mismo
  túnel, si no se alcanza no se alcanza para ninguno. Las escrituras siguen pasando en modo degradado.
- Se retira el forzado `poolSize = 1` de `OltGatewayConfig` y del clonado interno del bus.
  `olt.gateway.session.pool-size` pasa a 2 por defecto; con 1 el bus degrada a un solo carril
  (válvula de escape para producción).
- El seed de `OltMgrOltModel.maxConcurrentCliSessions` baja de 4 a 2, alineado con la cuota real.
- `olt.gateway.sync.skip-when-write-running` pasa a `false` por defecto: con carriles dedicados ya no
  hace falta cancelar ciclos enteros de inventario y señal mientras corre un alta.

**Pruebas.** `OltCliBusTest` reescrito para el modelo de dos carriles (17 casos): una escritura corre
mientras el fondo sigue ocupado, cada carril serializa lo suyo, cada carril usa su propia sesión,
el anidado no salta de carril en ninguna dirección, keepalive en las dos sesiones, degradación a un
carril con `pool-size=1`.

**Qué vigilar en el primer despliegue.** Que la OLT no rechace la segunda sesión (`display ssh server
session`) y que la 3.ª siga libre para el backup nocturno.

---

## F5 — Lecturas de SmartOLT servidas desde el inventario propio

**Problema.** `GET /onu/unconfigured_onus` y `GET /onu/getBySn` iban a SmartOLT en vivo en cada
llamada, y los `POST /admin/sync/*` ejecutaban el ciclo completo en el hilo HTTP (se midieron
859 s de petición bloqueada).

### Autofind propio

Nueva tabla `olt_mgr_onu_autofind` (entidad `OltMgrOnuAutofind`, migración `V41`) que cachea el
`display ont autofind all` de la OLT.

| Pieza | Rol |
|-------|-----|
| `OltAutofindCacheWriter` | Persistencia transaccional: upsert por SN normalizado, poda de los SN que ya no aparecen, y filtro de las ONU ya configuradas al leer |
| `OltAutofindCacheService` | Orquesta el refresco (fondo/vivo), guarda el último resultado y expone `status()` |
| `OltAutofindRefreshScheduler` | Refresco periódico (`olt.gateway.autofind.refresh-interval-ms`, 30 s) |

- El refresco periódico entra por `CliJobType.AUTOFIND_POLL`, es decir por el **carril de fondo** de F4.
- `GET /onu/unconfigured_onus?refresh=true` fuerza una lectura en vivo por `ADHOC` (carril interactivo)
  con timeout duro `olt.gateway.autofind.live-timeout-ms` (10 s), para que el técnico nunca espere más
  que eso.
- `first_seen_at` se conserva entre refrescos: sirve para saber cuánto lleva una ONU esperando alta.
- Si el autofind propio está apagado (`olt.gateway.autofind.enabled=false`) el endpoint vuelve a SmartOLT.

### getBySn

`OnuService.getOnuBySn` pasa a resolver por `OltManagerFacade` (BD primero, CLI como respaldo) y
mapea al DTO legado `OnuBySnResponse` que ya consumen Android y el back office. Un SN desconocido
por el gateway devuelve `404` **sin** consultar SmartOLT; solo un fallo del gateway (OLT inalcanzable)
cae a SmartOLT.

### Sync administrativos asíncronos

`OltGatewaySyncJobRunner` saca el trabajo del hilo HTTP: `POST /admin/sync/inventory`,
`/admin/sync/snmp-inventory` y `/admin/sync/signal` responden al instante un `SyncJobStatusDto`
(`job`, `started`, `running`, `skippedReason`, último resultado conocido). El resultado se consulta
por `GET /admin/sync/status`. Si ya hay un ciclo corriendo se responde `already_running` sin encolar
un segundo.

### Timeout de alarmas

`NetDiagOltCliAdapter` deja de usar el timeout fijo del `display alarm active all` y toma
`olt.gateway.sync.alarm-command-timeout-ms` (120 s por defecto), para que el volcado no se solape
con el siguiente ciclo del carril de fondo.

**Pruebas.** `OltAutofindCacheWriterTest` (normalización, reuso de fila, poda, cache vacío, filtro de
configuradas), `OltAutofindCacheServiceTest` (carril de fondo vs vivo, timeout configurado, fallo sin
excepción, estado), `OltGatewaySyncJobRunnerTest` (no se ejecuta en el hilo que responde,
`already_running`, fallo contenido), más los casos nuevos de `OnuServiceTest` y
`OltGatewayControllerTest`. Suite completa: 1673 pruebas en verde.

**Claves nuevas.** `olt.gateway.autofind.{enabled,refresh-interval-ms,initial-delay-ms,live-timeout-ms}`
y `olt.gateway.sync.alarm-command-timeout-ms`, con `${OLT_GATEWAY_*}` en `prod` y `dev`.

---

## F6 — Consultas calientes del backend y polling del back office

**Problema.** Cuatro pantallas del back office repetían llamadas por fila o por estado, y tres
trabajos del backend cargaban en memoria más de lo que necesitaban.

### Contadores del inbox de WhatsApp

`GET /whatsapp/conversations/view-counts` resolvía ocho consultas por teléfono. Se colapsan en una
sola proyección agregada `WhatsAppInboundMessageRepository.aggregateInboxSignalsByPhoneIn`, que
devuelve por teléfono el último entrante, el último saliente, el último adjunto (imagen o PDF) y la
última pulsación de «hablar con asesor» con un `UNION ALL` entre `whatsapp_inbound_message` y
`whatsapp_message_log`. `WhatsAppConversationQueryService.buildViewCountSignals` sustituye a
`buildSummariesForPhones` para este caso: los contadores no necesitan el resumen completo.

En el cliente, `useWhatsAppInboxViewCounts` reemplaza el efecto que volvía a pedir los contadores
cada vez que cambiaba la lista de conversaciones (debounce de 800 ms, es decir prácticamente por
mensaje). Ahora se piden una vez al montar y se resincronizan **como máximo una vez cada 20 s**, y
solo si llegó algún evento nuevo por WebSocket. Si la resincronización falla se conserva el último
valor conocido en vez de caer a cero.

### Ventana de servicio de 24 h

`WhatsAppMessageCandidateDto` incorpora `serviceWindowOpen` y `serviceWindowExpiresAt`, que
`WhatsAppBackofficeMessageService.withServiceWindows` resuelve en lote para todos los candidatos.
`WhatsAppServiceWindowIndicator` pasa a ser un componente presentacional: ya no hace un GET por
fila, pinta lo que trae el listado. Con 500 candidatos eso son 500 peticiones menos.

### Conteos de tickets

Nuevo `GET /assistanceTicket/counts` (`AssistanceTicketCountService`) que devuelve
`{ byStatus, open, closed, total }` con un solo `GROUP BY status`. La pantalla de tickets lanzaba
**7 `findAll`** al montar y **7 más por cada evento WebSocket**; ahora lanza una. Se elimina también
el `reloadTickets()` duplicado del montaje (dos efectos pedían el mismo filtro), con lo que el
arranque baja de 9 peticiones a 2.

### Badge de netdiag

Con el resumen ya materializado en F2, el badge sube de 30 s a 60 s de intervalo y estrena timeout
propio (`NETDIAG_SUMMARY_TIMEOUT_MS`, 30 s) en vez de heredar los 10 s del axios global. Además deja
de mostrar cero cuando la petición falla: conserva el último valor conocido, porque un timeout no
significa que no haya alertas.

### Consultas de traffic

- Se elimina el CTE `WITH ranked` (`summarizeInBucketRange`, 21 s medidos) y su proyección: el
  ranking ya se calculaba sobre los agregados existentes, la consulta era código muerto.
- `TrafficAnomalyService` deja de cargar 35 días de `subscription_traffic_hourly` de golpe cada
  60 s. Ahora pide los IDs distintos del rango (`findDistinctSubscriptionIdsInBucketRange`) y evalúa
  en lotes de `traffic.anomaly.evaluation-batch-size` (200 por defecto).

### EAGER de Subscription

La entidad traía nueve relaciones en EAGER. Se pasan a LAZY las cuatro que ningún listado proyecta:
`ipPool`, `cpe`, `coupon` y `additionalDevices` (esta última era la peor: un `@ManyToMany` EAGER
sobre `installed_devices`). Las cinco que `toDto()` sí lee (`plan`, `place`, `napBox`, `technician`,
`hostDevice`) se dejan EAGER a propósito: cambiarlas obligaría a auditar todos los llamadores fuera
de transacción sin ganancia real, porque los listados grandes ya usan `JOIN FETCH`.

- `findAllWithCoreRelations` (la consulta de `/subscription/all`) deja de traer `ipPool`, `cpe` y
  `coupon`: nadie los proyecta. Mantiene el `JOIN FETCH` de `additionalDevices`, que `toDto()` sí usa.
- `toDto()` aplica a `additionalDevices` la misma guarda `Hibernate.isInitialized` que ya usaban los
  pagos, para que siga siendo seguro fuera de sesión.
- `fiberOnu` no se toca: lleva `@NotFound(IGNORE)`, que en Hibernate fuerza carga temprana.

**Pruebas.** Backend: `WhatsAppInboxViewCountsTest`, `WhatsAppCandidateServiceWindowTest`,
`AssistanceTicketCountsTest`, `TrafficAnomalyBatchingTest`, `SubscriptionFetchStrategyTest`.
Back office: `useNetDiagOpenP0Count.test.ts` (60 s y valor conservado ante fallo),
`netdiagService.test.ts` (timeout propio), `ticketService.counts.test.ts`,
`WhatsAppServiceWindowIndicator.test.tsx` (no llama al backend),
`useWhatsAppInboxViewCounts.test.ts` (una llamada al montar, ninguna por mensaje, resincronización
acotada). Suite del back office: 900 pruebas en verde.

**Claves nuevas.** `traffic.anomaly.evaluation-batch-size`.

**Orden de despliegue.** Primero el backend (los campos nuevos del DTO y el endpoint de conteos son
aditivos, el cliente antiguo los ignora) y después el back office.

## F7 — Escrituras de ONU: enrutado por operación y modo sombra

**Problema.** Autorizar, borrar, reiniciar y mover ONU se hacía siempre por SmartOLT. El gateway ya
tenía los comandos CLI, pero nunca se han ejecutado en producción (`olt.gateway.writes.enabled` sin
definir), así que no se puede conmutar de golpe.

### Enrutado por operación

`OnuWriteRouter` centraliza las cuatro escrituras y decide el proveedor con
`olt.provider.{authorize,delete,reboot,move}` = `SMARTOLT` (por defecto) o `GATEWAY`.
`OnuService` deja de llamar a `OltService` y delega todo en el router, con lo que el contrato
`/onu/*` que consumen Android y el back office no cambia. Volver atrás es cambiar una propiedad.

Si una operación se enruta al gateway pero `olt.gateway.enabled=false`, el router lo registra y la
aplica por SmartOLT en vez de fallar. El orden de encendido previsto es **reboot → delete → move →
authorize**, de menor a mayor riesgo.

Al enrutar por SmartOLT una operación que llega con identificador propio, el router lo traduce:
resuelve el SN por el inventario propio y con él pide a SmartOLT su `unique_external_id`. Así el
identificador que ve el cliente puede ser el propio aunque la escritura la siga aplicando el SaaS.

### Identificador externo propio y estable

`OnuExternalIdPolicy` fija el formato `{oltId}_{board}_{port}_{onuIndex}`. Tres cambios lo hacen
estable:

- `SmartOltImportService` ya no acepta el `unique_external_id` que venga de SmartOLT: siempre genera
  el propio. El emparejamiento del import es por SN, así que el cambio no rompe la reconciliación.
- `OltManagerFacade.moveOnu` deja de reescribir el `externalId` al mover una ONU. El formato es
  posicional, pero el identificador no se regenera: una ONU movida conserva el que tenía.
- `POST /api/olt-gateway/admin/onus/external-id-backfill` (`OnuExternalIdBackfillService`) reescribe
  los heredados. Es idempotente: salta los que ya son propios, salta las ONU borradas y no reescribe
  cuando el identificador propio ya lo ocupa otra ONU (lo cuenta como colisión y lo registra).

### Modo sombra de authorize

Con `olt.provider.authorize-shadow=true` el alta se aplica por SmartOLT y después
`OltManagerFacade.recordAuthorizeShadow` compara lo que habría hecho el gateway
(`planAuthorize`, que calcula posición y comandos CLI **sin** ejecutarlos ni exigir
`writes.enabled`) contra lo que aplicó SmartOLT. La comparación es por posición —board, port,
ontId— y no por la cadena del identificador, porque el formato de SmartOLT es distinto por
definición y compararlo solo generaría ruido. Las divergencias van al log; nada se aplica.

**Pruebas.** `OnuWriteRouterTest` (enrutado por defecto y por operación, traducción de
identificadores, `deleteBySn`/`rebootBySn`, sombra), `OltManagerFacadeTest` (plan de alta sin tocar
la OLT, sombra, estabilidad del identificador al mover), `OltGatewayCommandServiceTest`
(`planAuthorize` devuelve la secuencia sin exigir escrituras), `OnuExternalIdPolicyTest`,
`OnuExternalIdBackfillServiceTest`, `OnuServiceTest` y `OltGatewayControllerTest`. Suite del
subsistema: 410 pruebas en verde.

**Claves nuevas.** `olt.provider.authorize`, `olt.provider.delete`, `olt.provider.reboot`,
`olt.provider.move`, `olt.provider.authorize-shadow`.

**Orden de despliegue.** Desplegar con los cuatro proveedores en `SMARTOLT` y la sombra encendida,
correr el backfill de identificadores y leer el log de divergencias antes de conmutar la primera
operación. Las escrituras CLI del gateway siguen requiriendo `olt.gateway.writes.enabled=true` y
deben validarse en entorno controlado antes de tocar producción.

## F8 — Cohesión: ONU, ACS canónico y puertos entre servicehealth y wispadmin

**Problema.** `servicehealth` importaba repositorios y entidades de `wispadmin` (23 usos
directos). El vínculo ACS se resolvía con fallbacks `genieacsDeviceId ?: tr069DeviceId` y la
relación `Subscription.fiberOnu` no tenía `@JoinColumn` explícito.

### Puertos

`AcsSubscriptionPort` y `SubscriptionDirectoryPort` viven en `servicehealth/port`, con adapters
en `wispadmin/adapter` (`AcsSubscriptionAdapter`, `SubscriptionDirectoryAdapter`), el mismo
patrón que `HealthOnuPort`. `servicehealth` ya no inyecta `SubscriptionRepository` ni
`SubscriptionAcsRepository` en Identity, Evidence, RemoteAction, Telemetry, Scope ni el
controlador. `ServiceHealthSubscriptionContextReader` conserva la proyección JPA
`ServiceHealthSubscriptionView`: es un DTO de lectura, no el registro ACS.

GenieACS (`GenieAcsClient`, `Tr069ModelProfiles`) sigue en `wispadmin` porque el cliente HTTP
es compartido con la provisión; sacarlo sería extraer ACS, no crear un port.

### `subscription_acs` canónico

`IdentityService.resolveAcs` y `RemoteActionService` leen solo `subscription_acs`.
`AcsTelemetryService` deja de mezclar `tr069DeviceId`. `SubscriptionAcsOpsService` ya no
cae a `subscription.tr069_*` ni a los SSIDs de `subscription` al refrescar. Los campos
`tr069ProvisionStatus`, `provisioningPending`, `tr069Message`, `tr069RequiresManualConfig` y
los cuatro WiFi **siguen en el DTO** de alta (`SubscriptionDto`) porque los consumen Android
y el back office. `tr069DeviceId` y `tr069LastError` quedan deprecados: se siguen escribiendo
en provisión (el alta aún los necesita) pero ya no los lee el diagnóstico.

`V42__subscription_acs_canonical.sql` copia deviceId, lastError, status y SSIDs de
`subscription` hacia `subscription_acs` cuando faltan, y declara la FK
`subscription.fiber_onu_sn → onu.sn` si no hay huérfanos.

### ONU unificada

`Subscription.fiberOnu` declara `@JoinColumn(name = "fiber_onu_sn")`. La tabla `onu` queda
como vínculo con la suscripción (8 columnas, PK `sn`); el inventario vive en `olt_mgr_onu`
y se consulta por SN a través de `HealthOnuPort`. No hay JOIN SQL entre ambas: el
emparejamiento es por serial.

### IdentityChangePublisher

El pointcut deja de interceptar todos los `save` del monolito: se acota a
`SubscriptionRepository` y `SubscriptionAcsRepository`. El observador ya no recarga la
entidad: llama `identity.reconcile(id)` contra el directorio.

### Flag `traffic.enabled`

Era huérfano: no existía en properties y `HealthTrafficAdapter` lo usaba con
`matchIfMissing=true`. Se elimina el `@ConditionalOnProperty`. El control real del
subsistema sigue siendo `gigafiber.subsystems.traffic.enabled` y `traffic.poll.enabled`.

**Pruebas.** `SubscriptionHealthAdaptersTest`, `IdentityServiceTest` (ACS solo canónico),
`ServiceHealthScopeTest`, `RemoteActionPersistenceTest`, `HealthWiringTest`,
`SubscriptionAcsOpsServiceTest` (reboot no usa el deviceId heredado),
`SubscriptionFetchStrategyTest` (FK `fiber_onu_sn`). Suite de servicehealth: 94 pruebas
en verde.

**Endpoints preservados.** `POST /subscription/{id}/acs/retry-tr069` y
`GET /subscription/{id}/registration-progress` no cambian.

## F9 — Gobierno del esquema y escalado

**Problema.** El esquema de producción lo iba creando Hibernate (`ddl-auto=update`). Había
dos `V25__*.sql` y Flyway no estaba en el classpath. Un backup de negocio arrastraba
~10 GB de telemetría.

### Flyway

- Dependencia `flyway-core` (versión del parent Boot 2.7).
- `spring.flyway.enabled=false` en el properties base para que los tests H2 no
  ejecuten DDL de MySQL.
- En prod y dev: `enabled=true`, `baseline-on-migrate=true`, `baseline-version=38`.
  Así las migraciones históricas (incluida la colisión de `V25`) no se reaplican.
- `V25__subscription_tr069_wifi.sql` se archiva en `db/archive/` para que Flyway
  no vea dos `V25`. `V25__subscription_vlan.sql` se queda como histórico.

### `ddl-auto`

Prod pasa a `validate`. Dev se queda en `update` para spikes locales. A partir de
aquí todo cambio de esquema va por Flyway (`V39`–`V43` ya cubren lo de este
refactor). `validate` debe probarse contra una copia del esquema real antes del
despliegue: si Hibernate declara un índice que la base no tiene, el arranque falla.

### Telemetría

`telemetry.datasource.*` declara un segundo DataSource. Si la URL está vacía se
reutiliza el de negocio (estado actual). `V43` crea `ispadmin_telemetry` en el
mismo MySQL. El corte operativo es: mover `net_diag_*`, `obs_*` y `*traffic*` a
ese esquema y poner `TELEMETRY_DATASOURCE_URL`. Los EntityManager siguen en el
datasource primario hasta ese corte; el bean ya existe.

### Retención unificada

`TelemetryRetentionCoordinator` ejecuta los tres puertos (netdiag, observability,
traffic) en un solo cron (`telemetry.retention.cron`, 03:20). Los crons
individuales de retención se quitan; el rollup de traffic y el purge de
`probe_run` de netdiag se quedan donde estaban.

**Pruebas.** `SchemaGovernancePropertiesFileTest`, `TelemetryRetentionCoordinatorTest`,
`TelemetryDataSourceConfigTest`.

**Claves nuevas.** `TELEMETRY_DATASOURCE_URL`, `TELEMETRY_DATASOURCE_USERNAME`,
`TELEMETRY_DATASOURCE_PASSWORD`.

**Orden de despliegue.** 1) Backup. 2) Arrancar con Flyway baseline 38 (las
`V39+` se aplican). 3) Confirmar `validate` contra una copia. 4) Mover
telemetría cuando se quiera un backup de negocio más liviano.


## SoT OLT/ONU (2026-09-02)

Ver `.agent-docs/oltgateway-fuente-verdad-onu-2026-09-02.md`. Defaults de escritura conmutados a `GATEWAY`; `subscription.fiber_onu_sn` deja de depender de la entidad legacy `onu`; lectura unificada vía `OltInventoryPort`.
