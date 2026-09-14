# Fase 14 — Métricas de infraestructura/JVM + pool de conexiones de BD

Muestreo periódico de métricas de la JVM, del sistema operativo y del pool HikariCP,
persistidas como serie temporal en el módulo de observabilidad, expuestas por API y
publicadas en vivo por STOMP para el dashboard dedicado (`ispadmin-observability-web`).

## Enfoque

Se añade `spring-boot-starter-actuator` (arrastra Micrometer `micrometer-core`). Micrometer
se usa **en proceso**: no se exponen endpoints Actuator por HTTP salvo `health`
(`management.endpoints.web.exposure.include=health`). Un `@Scheduled` lee del `MeterRegistry`
los medidores que Spring Boot 2.7 auto-registra (JVM memoria/GC/hilos, CPU proceso/sistema y
`hikaricp.connections.*`) y los vuelca a una entidad propia, coherente con el patrón casero
del resto del módulo (colector/scheduler → entidad → repo → query service → controller).

La memoria física del SO (`os_mem_free/total`) no la cubre Micrometer core, así que se lee de
`com.sun.management.OperatingSystemMXBean` con degradación segura si no está disponible.

## Componentes

- **Dependencia**: `spring-boot-starter-actuator` en `pom.xml`.
- **Config**:
  - `management.endpoints.web.exposure.include=health` y `spring.task.scheduling.pool.size=2`
    (evita contención entre el flush de APM/spans y el muestreo de sistema) en
    `application.properties`.
  - `ObservabilityProperties`: nueva sub-clase `SystemProperties` (`enabled`,
    `sampleIntervalMs=15000`, `publishLive`) accesible como `observability.system.*`; y
    `RetentionProperties.systemMetricDays=7`.
- **Entidad** `entity/ObsSystemMetric` (tabla `obs_system_metric`, índices en `bucket_start` y
  `sampled_at`): heap usado/máx, non-heap, CPU proceso/sistema, memoria SO libre/total, hilos
  vivos/daemon/pico, GC (count/timeMs acumulados), pool activas/idle/espera/total/máx.
- **Repositorio** `repository/ObsSystemMetricRepository`:
  `findByBucketStartBetweenOrderByBucketStartAsc`, `findFirstByOrderBySampledAtDesc`,
  `deleteOlderThan` (`@Modifying`).
- **Scheduler** `scheduled/ObsSystemMetricScheduler`:
  `@Scheduled(fixedRateString = "${observability.system.sample-interval-ms:15000}")`. Lee del
  `MeterRegistry` (`jvm.memory.used|max` por área heap/nonheap, `jvm.gc.pause` timers,
  `system.cpu.usage`, `process.cpu.usage`, `jvm.threads.live|daemon|peak`,
  `hikaricp.connections.active|idle|pending|max` y `hikaricp.connections` total) + MXBean del SO,
  persiste una fila y, si `publishLive`, publica en vivo.
- **Live** `service/ObsLivePublisher.publishSystemMetric(...)`: reutiliza el topic
  `/topic/observability/events` con discriminador `"type": "OBS_SYSTEM_METRIC"` (el cliente ya
  escucha ese único topic y filtra por `type`).
- **Query service** `service/ObsSystemMetricQueryService`: `timeSeries(from,to)` y `latest()`.
- **DTO** `dto/MetricDtos.SystemMetricPointDto`.
- **Controller** `controller/ObservabilityMetricController`:
  - `GET /observability/metrics/system?from&to`
  - `GET /observability/metrics/system/latest`
- **Retención** `scheduled/ObsRetentionScheduler`: purga diaria de `obs_system_metric` usando
  `retention.systemMetricDays`.

## Configuración por entorno

- `application.properties`: valores por defecto de `observability.system.*` y
  `observability.retention.system-metric-days`.
- `application-dev.properties`: habilitado con intervalo 15s.
- `application-prod.properties`: por variables de entorno `OBS_SYSTEM_ENABLED`,
  `OBS_SYSTEM_INTERVAL_MS`, `OBS_SYSTEM_PUBLISH_LIVE`, `OBS_SYSTEM_RETENTION_DAYS`.
- `scripts/docker/env.prod.example`: documenta las nuevas variables `OBS_SYSTEM_*`.

## Notas

- Las métricas de HikariCP quedan enlazadas por Micrometer al `HikariDataSource` durante la
  auto-config, con independencia de que el `DataSource` esté envuelto por `datasource-proxy`. Si
  en algún entorno no aparecen los medidores `hikaricp.*`, la alternativa es desenvolver con
  `dataSource.unwrap(HikariDataSource::class.java)` y leer `hikariPoolMXBean`.
- `gc_count`/`gc_time_ms` se guardan como valores acumulados; el dashboard puede graficarlos tal
  cual o derivar deltas.

## Validación

- `./mvnw -q -DskipTests compile`: OK (resuelve `spring-boot-starter-actuator` y compila el
  módulo sin errores).
