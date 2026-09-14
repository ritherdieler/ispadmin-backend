# Gradle multi-módulo y WAR único

El backend se construye con **Gradle 8.13** (Kotlin DSL + `gradle/libs.versions.toml`). El artefacto desplegable es un solo WAR: `:core` → `core/build/libs/ispadmin.war`.

El único build es Gradle. No hay wrapper ni proyecto Maven en el repo.

`SatelliteCompilationProfileTest` y `WarSubsystemPackagingTest` (perfiles Maven `*-war`) se sustituyeron por `LayeringTest`. Tras borrar un test, hay que `clean` del módulo: Gradle incremental puede dejar el `.class` y `:core:test` vuelve a exigir el descriptor Maven raíz.

## Capas

Un módulo solo puede depender de capas **más internas**. Lo garantiza el grafo de proyectos (`implementation` no es transitivo) y `LayeringTest`. `:core` engancha los satélites con `runtimeOnly` para que entren al WAR sin entrar al `compileClasspath`.

| Capa | Módulos |
|------|---------|
| L1 Plataforma | `:shared` (paquetes `shared`, `events`, `transport`, `routeros`) |
| L2 Satélites | `:acs`, `:oltgateway`, `:traffic` |
| L3 Composición | `:core` (`wispadmin`, `servicehealth`, `netdiag`, `observability`; plugin `war` y `mainClass` de `WispAdminApplication`) |

```text
L1  shared  (events, transport, routeros)
                 │
L2     acs  oltgateway  traffic
                 │  runtimeOnly
L3              core  →  ispadmin.war
```

`:servicehealth` (ahora dentro de `:core`) no importa `com.dscorp.wispadmin.wispadmin`. Habla con core por ports (`SubscriptionDirectoryPort`, `SubscriptionActionPort`, `CpeProvisionFlagPort`). Lo garantiza `LayeringTest.platformAndHealthDoNotImportCore`.

## Runtime

Un solo contexto Spring:

- DataSource / EMF / `JpaTransactionManager` / Flyway propios: `acs.*`, `oltgateway.*`, `traffic.*`.
- El DataSource y el `entityManagerFactory` de negocio son `@Primary` (`PrimaryDataSourceConfig`, `CoreJpaConfig`) para que JPA auto-config y `@EnableJpaRepositories` del core no queden sin beans cuando hay unidades de persistencia satélite. `SatelliteJpa` aplica las naming strategies de Spring Boot (`nap_box`, no `NapBox`).
- Migraciones satélite `ALTER` deben ser idempotentes (`information_schema.COLUMNS`). `V2__cpe_wifi_last_state.sql` también mira `information_schema.TABLES`: un `stg_acs` vacío no tiene `cpe_record` (la crea Hibernate después). `SatelliteJpa.migrateIfEnabled` llama `repair()` antes de `migrate()` para limpiar un V2 fallido en MySQL (sin DDL transaccional).
- Core conserva el primario (entidades wispadmin, netdiag, observability, servicehealth).
- Prestaging (`dev,local-prestaging`) apaga `mikrotik.connection.mock.enabled` para hablar con MK2 (`network_device` id 8), no con el mock de `dev`.
- Cuatro `SecurityFilterChain` con `@Order` y matcher: ACS `/api/acs/v1/**`, Gateway `/api/olt-gateway/**`, Traffic `/api/traffic/v1/**` + `/traffic/**`, core catch-all.
- Clientes HTTP entre módulos siguen en loopback (`*.internal-base-url` del mismo context-path).
- `AcsApplication` / `OltGatewayApplication` / `TrafficApplication` no hacen `@ComponentScan`: el WAR único ya escanea esos paquetes. Un scan anidado duplica `@RestController` (`Ambiguous mapping`).
- Rutas públicas `/traffic/network/**` y `/traffic/bandwidth/v1/**` las sirve el core (`BandwidthIntelligenceFacadeController`). Los controllers homónimos del módulo traffic quedan detrás de `traffic.legacy-context-paths=true`.
- STOMP `/app/subscription-traffic/*`: en el WAR único gana `CoreTrafficStreamRelay` (`traffic.client-enabled=true`). `SubscriptionTrafficWebSocket` del módulo traffic solo carga si `traffic.client-enabled=false` (WAR traffic aislado). Si ambos viven, Spring falla con `Ambiguous mapping`.

## Comandos

```bash
./gradlew test
./gradlew :core:war :core:tomcatLibs -Pdjl.linux
./gradlew :core:bootRun
./scripts/run-local-prestaging.sh start
./scripts/deploy.sh --env staging
```

DJL/PyTorch no viaja dentro del WAR. `tomcatLibs` escribe `core/build/tomcat-lib/` (nativos Linux con `-Pdjl.linux`). Modelos faciales: `core/src/main/resources/models` → rsync a `/opt/gigafiber/models`.

`scripts/verify-war.sh` exige `WEB-INF/classes/com/dscorp/wispadmin/{wispadmin,servicehealth,netdiag,observability,routeros}/` y `WEB-INF/lib/{acs,oltgateway,traffic}.jar`. Las clases de `:core` van en `WEB-INF/classes`; los satélites siguen como JAR.

Staging (`tomcat-staging`) fija `SPRING_PROFILES_ACTIVE=prod,staging`. El `application.properties` del WAR sigue en `dev,local` para el Mac; sin el env del contenedor ACS Flyway apunta a localhost y el contexto queda en 404.

Los DataSource satélite (`acs.*`, `oltgateway.*`, `traffic.*`) no leen `SPRING_DATASOURCE_*` solos. En el VPS Tomcat tiene `SPRING_DATASOURCE_PASSWORD` y no `DB_PASSWORD`. El WAR usa `${ACS_DATASOURCE_PASSWORD:${spring.datasource.password}}` (igual oltgateway/traffic). `ensure-tomcat-staging-compose.py` copia el valor de `SPRING_DATASOURCE_PASSWORD` a `ACS_DATASOURCE_PASSWORD`, `OLTGATEWAY_DATASOURCE_PASSWORD` y `TRAFFIC_DATASOURCE_PASSWORD` para un WAR ya desplegado que aún resolvía `${DB_PASSWORD:}`.

El primer WAR Gradle (DJL 0.36 / PyTorch 2.7.1) exige `./scripts/deploy.sh --setup --env staging` para copiar `core/build/tomcat-lib` a la imagen. `PytorchNativeHelper` no puede ir en `WEB-INF/classes`: Tomcat lo cargaría en el classloader del WAR y JNI falla (`UnsatisfiedLinkError torchSetGradMode`). Solo vive en `CATALINA_HOME/lib/ispadmin-djl-native-helper.jar`. Tras copiar el WAR, `deploy.sh` hace `docker restart` del Tomcat destino: un redeploy en la misma JVM deja `libgomp` cargado en el classloader viejo.

Staging (`https://api.gigafiberperu.cloud/ispadmin-staging/`) quedó en pie el 2026-09-13: perfiles `prod,staging`, `Started WispAdminApplication`, raíz 200 y `/actuator/health` `UP`. El contexto exploded en `tomcat-staging` tiene `acs.jar` / `oltgateway.jar` / `traffic.jar` parcheados (Flyway ACS V2 idempotente, `@ComponentScan` anidado fuera, `traffic.legacy-context-paths`). Un `--war-only` con el `target/ispadmin-staging.war` anterior a esos jars revertiría el contexto. El siguiente deploy debe reconstruir `./gradlew :core:war :core:tomcatLibs -Pdjl.linux` y subir ese WAR.
