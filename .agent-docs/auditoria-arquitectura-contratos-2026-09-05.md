# Auditoría de arquitectura y contratos del backend — 2026-09-05

## Conclusión y alcance

El backend está en transición hacia un core público que orquesta WARs técnicos. La separación de OLT Gateway, ACS y Traffic ya existe en código y empaquetado, pero no está completa en persistencia, autenticación, eventos y contratos públicos. NetDiag, Service Health y Observability siguen siendo módulos opcionales del core; no deben describirse como WARs autónomos ya terminados.

Revisión del árbol de trabajo actual, incluidos numerosos cambios previos sin commit. Inventario estático de **86 controladores con mappings, 527 declaraciones HTTP de método y 172 interfaces Kotlin**. Se inspeccionaron las fronteras entre subsistemas, clientes HTTP, puertos, autenticación, eventos, empaquetado y pruebas. El inventario completo no implica validación funcional individual de los 527 mappings. No se verificaron despliegues, reverse proxy, permisos MySQL, clientes de otros repositorios ni equipos de red. No se modificó código de producción.

[Inventario completo](./arquitectura-contratos-inventario-2026-09-05.md): declaraciones HTTP/STOMP, interfaces y matriz de imports. Los mappings contados pueden incluir rutas alternativas y controladores desactivados por perfil; no son un conteo de endpoints activos en producción.

## Estructura observada

| Unidad | Responsabilidad y módulos | Integración actual |
|---|---|---|
| Core `ispadmin` / `ispadmin-staging` | Suscripciones, clientes, altas, autenticación, CRM WhatsApp, búsqueda, fachadas ONU/Traffic | HTTP hacia OLT Gateway y Traffic; recibe directorio de solicitudes de Traffic; consume eventos CPE |
| Módulos opcionales dentro del core | NetDiag, Service Health/360 y Observability | NetDiag y Health consultan Gateway por HTTP; Health consulta Traffic; todavía existen adapters e imports al dominio core y entre módulos |
| WAR `ispadmin[-staging]-oltgateway` | Inventario ONU/OLT, SSH/SNMP, compatibilidad SmartOLT, activación y orquestación CPE | HTTP a ACS; publica eventos; posee entidades OLT propias |
| WAR `ispadmin[-staging]-acs` | Registro CPE, deviceId, estado, WiFi/WAN y TR-069 | Atiende Gateway por HTTP; integra GenieACS; posee `CpeRecord` |
| WAR `ispadmin[-staging]-traffic` | Routers, muestras, agregaciones, anomalías, analítica y streaming | HTTP al directorio core (targets y routers); RouterOS; Redis. El seed JDBC cruzado se retiró |
| Infraestructura compartida | `routeros`, `events` | Interfaces y adaptadores empaquetados en consumidores; no son WARs de negocio |

`pom.xml` contiene un único proyecto Maven de packaging WAR, con perfiles y exclusiones por paquete; no hay módulos Maven independientes por subsistema. `scripts/subsystems.sh` permite conservar Observability, NetDiag y Service Health como opcionales; Traffic/OLT Gateway se excluyen como implementación y se habilitan mediante clientes HTTP. El comportamiento final depende del perfil, los toggles y el comando de empaquetado utilizado.

## Mapa de contratos entre unidades

| Consumidor → proveedor | Contrato | Evaluación |
|---|---|---|
| Core → OLT Gateway | `/api/olt-gateway/*`, `X-Olt-Gateway-Key`; `OltGatewayHttpClient`, `GatewayOnuActivationClient` | Fachada de transporte y DTOs locales presentes; errores públicos y recuperación de operaciones incompletos |
| Core → Traffic | `/api/traffic/v1/*`, `X-Traffic-Key`; `TrafficHttpClient` | Reenvío de JSON crudo y query strings; el contrato público depende del JSON del satélite |
| Traffic → Core | `/internal/traffic/targets`, `/internal/traffic/routers`, `X-Traffic-Key`; `TrafficDirectoryPort` | DTOs equivalentes; cursor de targets; directorio de routers por HTTP (sin JDBC cruzado) |
| Gateway → ACS | `/api/acs/v1/cpe/*`, `X-Acs-Key`; `AcsCpeClient` | Timeout y JSON inválido no se traducen a FAILED de negocio; status 404 → ausente |
| NetDiag → Gateway | Descriptor, inventario PON, poll/parse de alarmas; cuatro puertos implementados por un cliente HTTP | URIs codificadas; errores de evidencia siguen degradando a vacío |
| Service Health → Gateway | `HealthOnuPort`, `HealthLabOpticalPort`, `HealthCpePort` | HTTP sin repositorio core; paginación óptica; SN encoded |
| Service Health → Traffic | `HealthTrafficPort`: muestras, runs, anomalías y configuración | HTTP implementado; errores degradan a ausencia de evidencia y configuración por defecto |
| NetDiag ↔ Core; Health ↔ NetDiag | Puertos y adapters con repositorios locales | **Siguen embebidos**; allowlist de adapters; no son WARs autónomos |
| Observability ↔ Core | `ObservabilityReporter`, tracing y autenticación de sesión | Integración en proceso; allowlist; no hay WAR propio |
| Gateway/Traffic → consumidores core | `PlatformEvent`, Redis Streams | Envelope versionado; reclaim/cuarentena; namespace Redis por entorno |
| Traffic → streaming | STOMP interno; core relay `/subscription-traffic/start` `/stop` | Cliente externo pasa por el core; handshake Traffic con clave interna |
| CRM WhatsApp | Controladores, servicios y publicación realtime en `wispadmin` | Módulo del core; no existe una frontera inter-WAR CRM que deba inventariarse como servicio autónomo |

## Hallazgos priorizados

### H1 · P1 · Rutas antiguas de Traffic fuera de autenticación

**Confirmado en código.** El perfil Traffic configura `anyRequest().permitAll()`, registra el filtro de API key exclusivamente en `/api/traffic/v1/*` y escanea todo el paquete Traffic. Persisten controladores sin restricción de perfil bajo `/subscription/*`, `/traffic/network/*`, `/traffic/bandwidth/v1/*` y POST `/traffic/poll` / `/traffic/aggregation/catch-up`. Estas rutas no atraviesan ese filtro. No se afirma exposición a Internet: falta revisar la red de despliegue.

**Acción:** denegar rutas no autorizadas por defecto; limitar el WAR técnico a su API interna y retirar/desactivar aliases públicos en ese perfil. **Prueba exigida:** arrancar el contexto Traffic y comprobar GET y POST sin clave, clave incorrecta y clave válida, incluidos todos los aliases.

Evidencia: [TrafficSecurityConfig](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/config/TrafficSecurityConfig.kt:20), [TrafficWebConfig](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/config/TrafficWebConfig.kt:18), [SubscriptionTrafficPollController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/controller/SubscriptionTrafficPollController.kt:12).

### H2 · P1 · Traffic todavía depende del schema y credenciales del core

**Confirmado.** `TrafficRouterSeedRunner` hace `INSERT ... SELECT` desde `network_device` de otro schema, copiando host, usuario y contraseña en cada arranque cuando está habilitado. `application-traffic.properties` lo habilita y apunta al core. Viola la regla explícita contra JDBC cruzado y exige permisos SQL ajenos al dueño del WAR.

**Acción:** directorio interno específico de dispositivos o carga administrativa propia de Traffic; migración inicial controlada y posterior retiro del seed cruzado. No ampliar el directorio público ni incorporar credenciales al DTO de suscripciones. **Prueba:** Traffic inicia y resuelve sus routers con un usuario MySQL limitado a su schema.

Evidencia: [TrafficRouterSeedRunner](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/config/TrafficRouterSeedRunner.kt:18).

### H3 · P1 · El pull óptico solo procesa 200 ONUs y renueva artificialmente su fecha

**Confirmado.** `pullOptical()` y `pullStates()` piden `size=200` sin recorrer páginas. El proveedor tiene `page=0` por defecto y máximo de 200. A partir de la ONU 201 se omite telemetría en ese mecanismo de pull. Además, `pullOptical()` asigna `Instant.now()` a datos de inventario, mezclando hora de lectura con hora real de observación. Los eventos pueden aportar datos adicionales, pero no corrigen la cobertura incompleta del fallback HTTP.

**Acción:** iterar páginas o consumir cambios mediante cursor; conservar `observedAt` del origen y separar `fetchedAt`. **Prueba:** proveedor con 201/450 ONUs, página intermedia fallida y muestras antiguas.

Evidencia: [HealthOltGatewayHttpClient](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/client/HealthOltGatewayHttpClient.kt:101), [OltGatewayController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/controller/OltGatewayController.kt:189).

### H4 · P1 · Redis no recupera mensajes pendientes ni garantiza publicar cambios CPE

**Confirmado.** Los dos consumidores leen únicamente `lastConsumed()` y dejan sin ACK los registros que fallan, pero no implementan recuperación de pendientes. `ensureGroup()` trata cualquier excepción como grupo preparado/existente. Si Redis falla al crear el grupo, no hay recreación explícita posterior. El publicador captura errores de XADD y continúa, sin persistir intención de publicación. Para cambios CPE que actualizan flags de negocio, no basta la política de telemetría prescindible.

**Acción:** relectura/reclamación de pendientes, reintentos limitados, cuarentena de mensajes inválidos, recreación del grupo tras indisponibilidad e identificadores de consumidor por instancia. Para CPE: outbox durable o reconciliación periódica verificable contra el proveedor. **Pruebas:** caída antes/después de ACK, Redis ausente al iniciar, XADD fallido y reinicio del consumidor.

Evidencia: [HealthSnapshotConsumer](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/service/HealthSnapshotConsumer.kt:44), [CpeProvisioningEventConsumer](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/CpeProvisioningEventConsumer.kt:48), [RedisStreamEventBus](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/events/RedisEventBusConfig.kt:87).

### H5 · P1 · La operación de activación no sobrevive al reinicio del Gateway

**Confirmado.** El estado de activación y la asociación externalId→SN viven en `ConcurrentHashMap`; ACS se ejecuta mediante un executor local. Tras reiniciar, la consulta por SN pierde el estado y el fallback por externalId llama `acsCpeClient.status(externalId)`, aunque ACS consulta su registro por SN. Puede consultar una identidad incorrecta y devolver `NA`.

**Acción:** operación durable con `operationId`, SN normalizado, externalId y transición de estado; consulta por identificador correcto; deduplicación de peticiones y recuperación de trabajo pendiente. El manejo actual del conflicto “ONU ya autorizada” es útil, pero no equivale a idempotencia durable de toda la operación. **Pruebas:** reinicio con ACS pendiente, consulta con externalId distinto de SN y dos solicitudes concurrentes del mismo alta.

Evidencia: [OnuActivationService](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/service/OnuActivationService.kt:30), [CpeFacadeService](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/acs/service/CpeFacadeService.kt:63).

### H6 · P2 · Las fachadas pierden la semántica HTTP del proveedor

**Confirmado.** Los proxies capturan `RestClientException` y responden 503 indistintamente. Con el manejo de errores del `RestTemplate` usado, un 404 o 400 del proveedor también entra en esa rama. Se confunde un recurso inexistente o parámetros inválidos con una caída del subsistema. Varios clientes de evidencia, además, convierten cualquier excepción en null o lista vacía.

**Acción:** traducir errores conocidos a un contrato público estable: código, origen, correlación, retryable y detalles seguros; conservar semántica de validación/no encontrado/conflicto y distinguir timeout, indisponibilidad y respuesta incompatible. Los fallos de auth interna requieren tratamiento operativo, no copiar ciegamente un 401 al usuario. **Pruebas:** 400/404/409/429/500, timeout, JSON inválido y cuerpo vacío.

Evidencia: [SubscriptionTrafficFacadeController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/trafficclient/SubscriptionTrafficFacadeController.kt:41), [OnuFacadeController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/oltclient/OnuFacadeController.kt:66), [HttpAcsCpeClient](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/oltgateway/client/HttpAcsCpeClient.kt:77).

### H7 · P2 · El directorio Traffic puede bloquearse y reutilizar datos indefinidamente

**Confirmado.** Construye su propio `RestTemplate()` sin configurar timeouts. Si una actualización falla y existe cache, lo devuelve sin edad máxima. Un cambio de IP, router o baja puede quedar oculto de manera prolongada. El contrato `list()` descarga todo el directorio.

**Acción:** cliente inyectable con timeout explícito, antigüedad máxima y señal de degradación; paginación o cursor incremental para crecer. **Pruebas:** socket que no responde, cache expirada, baja de suscripción y cambio de router durante indisponibilidad.

Evidencia: [HttpTrafficDirectoryClient](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/client/HttpTrafficDirectoryClient.kt:20).

### H8 · P2 · Las pruebas de desacople dejan pasar dependencias de dominio

**Confirmado.** NetDiag consulta `SubscriptionRepository`; HealthNetDiagAdapter conecta repositorios NetDiag con puertos Health; Service Health importa dominio core; Observability depende de autenticación y tracing del core. Las reglas no prohíben varias de esas direcciones y exceptúan cualquier import a `port`. Son módulos embebidos actualmente: la consecuencia inmediata es que su separación como WAR aún no está lista, no que ya exista un fallo de red entre WARs.

**Acción:** declarar explícitamente qué módulos seguirán embebidos y cuáles se extraerán; para estos últimos, contratos HTTP propios del consumidor y eliminación de repositorios ajenos. Reglas exhaustivas de dependencia, incluyendo nombres completamente cualificados y consultas SQL cruzadas. **Prueba:** introducir una dependencia prohibida por cada dirección y verificar que el guardrail la detecta.

Evidencia: [WispAdminOntSubscriptionAdapter](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/adapter/WispAdminOntSubscriptionAdapter.kt:12), [HealthNetDiagAdapter](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/adapter/HealthNetDiagAdapter.kt:26), [SubsystemDependencyRulesTest](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/test/kotlin/com/dscorp/wispadmin/wispadmin/config/SubsystemDependencyRulesTest.kt:148). El inventario incluye la matriz completa de imports.

### H9 · P2 · El contrato de eventos no protege frente a versiones, reordenamiento ni datos parciales

**Confirmado.** `PlatformEvent` carece de versión de esquema, productor, correlación de operación e identificador lógico estable. `CpeProvisionFlagService` aplica estados sin comparar antigüedad/versión. La cache Redis actualiza hashes únicamente con campos no nulos: una muestra nueva sin Mbps/Rx puede conservar valores anteriores y renovarles el TTL. Para optical/state puede desearse un merge, pero requiere edad por campo y semántica explícita.

**Acción:** distinguir evento snapshot de patch; usar versión por entidad/operación, deduplicación y tiempos por dato. No usar únicamente un orden fijo de estados porque una nueva operación válida puede volver a PENDING. **Pruebas:** COMPLETE seguido de un PENDING antiguo, duplicados, esquema desconocido y una muestra sin señal después de otra válida.

Evidencia: [PlatformEvent](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/events/PlatformEvent.kt:5), [CpeProvisionFlagService](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/service/CpeProvisionFlagService.kt:11), [RedisLiveTelemetry](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/events/RedisEventBusConfig.kt:160).

### H10 · P2 · Falta completar la fachada pública del streaming de suscripción

**Confirmado en la búsqueda del backend; uso externo pendiente de verificar.** Los handlers y el publicador de `/topic/subscription-traffic/{id}` viven en Traffic. Su handshake valida tokens de usuario con el secreto de sesión de Observability. No se encontró relay del core para esos destinos. Separar el WAR deja pendiente el flujo requerido por la regla “cliente externo solo con core”. Los WebSockets de interfaces del core son otra funcionalidad.

**Acción:** core autoriza start/stop y suscripción al topic; transporte interno autenticado hacia Traffic y publicación al cliente desde core. **Prueba:** flujo STOMP completo por core, desconexión, reconexión y autorización por suscripción, con Traffic inaccesible desde el cliente.

Evidencia: [SubscriptionTrafficWebSocket](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/websocket/SubscriptionTrafficWebSocket.kt:54), [TrafficWebSocketHandshakeInterceptor](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/traffic/config/TrafficWebSocketHandshakeInterceptor.kt:20).

## Riesgos adicionales y mejoras de contrato

1. **Aislamiento Redis por entorno — condicionado al despliegue.** Stream, grupos y keys tienen nombres predeterminados compartidos (`gigafiber.events`, `snapshot-core`, `cpe-provision-core`, `health:*`). Los overlays revisados no dan namespace por entorno. Si staging y producción comparten instancia/base Redis, pueden mezclar eventos, cache y consumidores. Verificar topología real y exigir aislamiento explícito antes de coexistencia; probar el mismo subscriptionId en ambos entornos.
2. **Contratos públicos tipados y compatibilidad.** Core devuelve `ResponseEntity<String>` y reenvía query strings en varias fachadas. Mantener DTOs propios del consumidor es correcto; lo que falta es comprobarlos contra el proveedor. Versionar OpenAPI por WAR, validar compatibilidad en CI y ejecutar fixtures de proveedor→consumidor, incluidos enums, nullabilidad, unidades, paginación y fechas. OLT no versiona la URL como Traffic y ACS. La descripción OpenAPI del core todavía afirma incluir el Gateway. No compartir entidades JPA para resolver duplicación de DTOs. Evidencia: [SubscriptionTrafficFacadeController](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/trafficclient/SubscriptionTrafficFacadeController.kt:41), [OpenApiConfig](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/wispadmin/config/OpenApiConfig.kt:20).
3. **Construcción segura de URIs y JSON.** NetDiag interpola `name`/`oltName` en query strings y Health interpola SN en JSON. Usar parámetros URI codificados y serialización de DTOs. Probar nombres con `&`, `+`, espacios y comillas. Evidencia: [NetDiagOltGatewayHttpClient](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/netdiag/client/NetDiagOltGatewayHttpClient.kt:49), [HealthOltGatewayHttpClient](/Users/sergiocarrillo/gigafiber/ispadmin-backend/src/main/kotlin/com/dscorp/wispadmin/servicehealth/client/HealthOltGatewayHttpClient.kt:101).
4. **Resiliencia y trazabilidad por operación.** Hay timeouts explícitos en varios clientes (core→OLT: 5s/180s; core→Traffic: 3s/8s; Gateway→ACS: 3s/120s), pero no una política uniforme por lectura/comando. Centralizar construcción técnica de clientes, propagación de correlación y métricas de latencia/error; presupuestos por operación y reintentos solo cuando sean seguros. Separar trabajos largos de consultas rápidas. No basta añadir retries a comandos de provisión.
5. **Frontera de compilación.** Un solo POM compila dominios juntos y después excluye clases/jars para fabricar WARs. Evolucionar hacia módulos Maven por aplicación y librerías técnicas pequeñas haría que dependencias indebidas fallen al compilar. Mantener tests de arranque de cada WAR con su classpath real; los tests actuales de texto en POM no sustituyen esa validación.

## Orden propuesto y criterio de aceptación

| Orden | Trabajo | Evidencia de terminado |
|---|---|---|
| 1 | Autenticación Traffic y retiro de JDBC cruzado | Rutas sin clave rechazadas; Traffic funciona sin permisos al schema core |
| 2 | Corrección de paginación y fechas de evidencia | Cobertura >200 ONUs; dato antiguo nunca presentado como recién medido |
| 3 | Activación durable y recuperación de eventos | Reinicios/Redis caído no pierden resultados; identidades correctas |
| 4 | Errores, DTOs y contratos HTTP/STOMP | Compatibilidad proveedor-consumidor, errores diferenciados y clientes solo por core |
| 5 | Límites de módulos y empaquetado | Dependencias prohibidas detectadas y arranque aislado por WAR |

Toda implementación posterior debe seguir Red→Green→Refactor según AGENTS.md. Esta revisión no cambió lógica ni implementó correcciones; describe las pruebas que deben exigirlas antes de escribir producción.

## Verificación ejecutada

Auditoría inicial (solo lectura): 39 pruebas de caracterización. No se modificó producción en esa pasada.

## Estado post-corrección (mismo día, árbol local)

Implementación y pruebas: [correcciones-arquitectura-2026-09-05.md](./correcciones-arquitectura-2026-09-05.md).

| Hallazgo | Tras las correcciones |
|---|---|
| H1 | Rutas Traffic autenticadas; WS interno con `X-Traffic-Key` |
| H2 | Directorio HTTP de routers; seed JDBC cruzado retirado |
| H3 | Paginación >200 ONUs; fechas de observación del origen |
| H4 | Reclaim/cuarentena Redis; orden CPE |
| H5 | Journal durable, lease, timeout ACS incierto, recover |
| H6 | Fachadas traducen 400/404/409/503 y timeout/JSON inválido |
| H7 | Timeouts y TTL del directorio Traffic |
| H8 | Clientes HTTP aislados; módulos Health/NetDiag/Obs **siguen embebidos** con allowlist |
| H9 | Envelope versionado y deduplicación |
| H10 | Relay STOMP en el core; prueba de socket real |

Compilación aislada: `oltgateway-war`, `traffic-war`, `acs-war` en verde. Flyway: un solo `V39`; NetDiag en `V47`. Suite afectada: **101/101**. Sin commits ni deploys. No se corrió `./mvnw test` completo ni live OLT/ACS/Redis de VPS.
