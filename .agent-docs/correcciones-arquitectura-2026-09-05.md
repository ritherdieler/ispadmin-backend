# Correcciones de arquitectura — cierre 2026-09-05

Trabajo local sobre la auditoría e inventario del mismo día. Sin commits ni despliegues.

## Resultado

Los hallazgos **H1–H7, H9–H10** y la frontera HTTP de Gateway/Traffic/ACS están implementados y cubiertos por la suite afectada. **H8** queda cerrado como módulos embebidos con frontera HTTP aislada y allowlist; no se extraen Service Health, NetDiag ni Observability a WAR propios.

## Hallazgos

| Id | Estado | Qué quedó |
|---|---|---|
| H1 | Cerrado | Traffic deny-all + `X-Traffic-Key` en REST y handshake WS interno |
| H2 | Cerrado | Directorio HTTP de routers; sin JDBC cruzado al schema core |
| H3 | Cerrado | Paginación óptica/estados; `observedAt` del origen; cache con edad máxima |
| H4 | Cerrado | Reclaim de pendientes Redis, cuarentena, grupo recreable; orden/dedupe CPE |
| H5 | Cerrado | Journal JDBC + lease; timeout ACS no es FAILED; presupuesto de provision; recover |
| H6 | Cerrado | Errores HTTP públicos con semántica del proveedor (400/404/409/503, timeout, JSON inválido) |
| H7 | Cerrado | Timeouts del directorio Traffic y TTL de cache; cursor de targets |
| H8 | Cerrado con alcance | Clientes HTTP de Health/NetDiag sin dominio core. Adapters in-process allowlisted. **Siguen embebidos en el core** |
| H9 | Cerrado | Envelope de evento con versión/productor; orden y deduplicación CPE |
| H10 | Cerrado | Relay STOMP por el core; handshake Traffic solo con clave interna; STOMP real en Tomcat embebido |

## Empaquetado

`kotlin-maven-plugin` une `sourceDirs` con `compileSourceRoots`. Los perfiles satélite fijan `kotlin.main.source` al paquete del WAR para que el árbol del core no se compile.

Verificado:

```text
./mvnw -P oltgateway-war -DskipTests compile   # OLTGATEWAY_OK
./mvnw -P traffic-war -DskipTests compile      # TRAFFIC_OK
./mvnw -P acs-war -DskipTests compile          # ACS_OK
```

Flyway: `V39__netdiag_retention_and_indexes.sql` permanece como la versión ya aplicada en staging. `unique_external_id` de ONU va en `V48__onu_unique_external_id.sql`. No reutilizar números Flyway ya aplicados.

`OnuWriteRouter` del core se eliminó (el alta FIBER usa `GatewayOnuActivationClient`). `RealOltService` habla con `SmartOltHttpClient`; el helper estático `HttpClient` ya no existe.

## Pruebas

Suite afectada: **101 tests, 0 fallos, 0 errores, 0 skipped** (`BUILD SUCCESS`). Incluye frontera Traffic, STOMP real (`TrafficStompConnectionTest`, 2), activación/recovery ACS, URI encoding, paginación óptica, contratos HTTP, isolation de WARs, allowlist H8 y gobernanza V39.

No equivale a `./mvnw test` completo. `RedisRecoveryIntegrationTest` exige `REDIS_TEST_PORT` y no se ejecutó en este cierre.

## H8 — qué no se extrae

Service Health, NetDiag y Observability permanecen módulos opcionales del core. Imports al dominio `wispadmin` solo en adapters/servicios allowlisted (`SubsystemDependencyRulesTest.embeddedModulesImportCoreOnlyThroughAllowlistedAdapters`). Extraerlos como WAR implica directorio HTTP de suscripciones, sesión/tracing propios y notificador WhatsApp desacoplado; eso es trabajo posterior.

## Arranque Core en staging (2026-09-05)

El WAR core no empaqueta `oltgateway/**` ni `traffic/**`. `DashBoardService` cuenta ONUs con `OltGatewayHttpClient`. `TrafficTelemetryRetentionAdapter` no puede vivir en `shared`: Spring resuelve el genérico y exige `SubscriptionTrafficRetentionService` en el classpath. La retención de traffic corre en `SubscriptionTrafficMaintenanceScheduler` del WAR traffic.

`SubsystemDependencyRulesTest.coreShippedCodeDoesNotReferenceSplitWarTypes` recorre shared/wispadmin/events/transport y módulos embebidos buscando FQCN de wars split, no solo `import`.

Satélites en staging: Hibernate `ddl-auto=update` (no el `validate` de `application-prod.properties`). Actuator `/actuator/health` permitido en Traffic. `management.health.redis.enabled=false` en traffic/oltgateway/acs (el Redis de Spring Boot no es `gigafiber.redis`). ACS staging: `acs.profiles.catalog` y `gigafiber.redis.namespace` van en líneas distintas del overlay Ant.

Deploy staging (`tomcat-staging` :8081): core + oltgateway + traffic + acs en HTTP 200. `--with observability,netdiag,servicehealth,oltgateway,traffic,acs`.

## Pendiente fuera de este cierre

- OpenAPI versionado y fixtures proveedor→consumidor en CI.
- Módulos Maven reales (un POM sigue fabricando varios WARs).
- Suite Maven completa y pruebas live OLT/ACS/Redis de despliegue.
- Transmitir `OLT_GATEWAY_OPERATION_SECRET` en el VPS en el próximo deploy (nombre ya en catálogo y `deploy.config.example`).
