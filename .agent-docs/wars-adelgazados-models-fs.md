# WARs adelgazados (mismos 4 artefactos)

Build verificado: `mvnw package -DskipTests -Ddjl.linux` con perfiles `staging-war`, `acs-staging-war`, `traffic-staging-war`, `oltgateway-staging-war` (2026-09-05).

Los 4 WARs siguen siendo Core / ACS / Gateway / Traffic. No hay multi-módulo ni Spring en `CATALINA_HOME/lib`. DJL/PyTorch sigue en `/opt/gigafiber/tomcat/lib` (~244 MB en la imagen Tomcat).

## Tamaños (staging, post-package)

Antes cada WAR pesaba ~240 MB (`models/` ~114 MB + el mismo árbol `WEB-INF/lib`).

| Artefacto | Bytes | MB | Perfil Maven |
|-----------|------:|---:|--------------|
| `ispadmin-staging.war` (Core) | 132192632 | 126.1 | `staging-war` |
| `ispadmin-staging-acs.war` | 68532832 | 65.4 | `acs-staging-war` |
| `ispadmin-staging-traffic.war` | 68732927 | 65.6 | `traffic-staging-war` |
| `ispadmin-staging-oltgateway.war` | 75313641 | 71.8 | `oltgateway-staging-war` |

La suma (~329 MB) sigue por encima de un mono de 241 MB: Spring/Hibernate se copia cuatro veces. Ese es el techo sin classloader compartido de Boot.

## 1. Modelos faciales en disco

`maven-war-plugin` excluye `WEB-INF/classes/models/**` en **todos** los WARs.

Dev/local: `application-dev.properties` sigue en `classpath:models/…` (`src/main/resources/models/` para `spring-boot:run`).

Prod y staging Core (bake `prod`):

| Propiedad | Path en Tomcat |
|-----------|----------------|
| `face.login.djl-model-path` | `/opt/gigafiber/models/face_feature.zip` |
| `face.login.djl-detector-model-path` | `/opt/gigafiber/models/ultranet.zip` |
| `face.embedding.model-path` | `/opt/gigafiber/models/arcface_w600k_mbf.onnx` |

Upload extra una vez (y en cada `--deploy` / `--setup`, rsync idempotente): ~114 MB en el host `/opt/gigafiber/models/` (`face_feature.zip` 99 MB, `arcface_w600k_mbf.onnx` 13 MB, `ultranet.zip` 1,4 MB).

`scripts/deploy.sh` (`upload_face_models`): rsync con **`-t`** (mtime). En deploys siguientes no reenvía los 114 MB si no cambiaron. Solo corre si Core está en el set (`--only`).

1. rsync de cada archivo (`face_feature.zip`, `ultranet.zip`, `arcface_w600k_mbf.onnx`). `run_rsync` no es recursivo: un directorio entero se salta.
2. Añade volumen `:ro` en `docker-compose.yml` si falta (`host:/opt/gigafiber/models`) bajo el servicio del env (`tomcat` o `tomcat-staging`). Un `--env staging` **no** edita el servicio prod.
3. Si el contenedor aún no tiene el mount, `docker cp` al path de Spring para no esperar un recreate.

Si falta un archivo, el primer login facial falla con el mensaje de `FaceModelFileResolver` (aceptable).

`scripts/verify-djl-war.sh` exige que los tres archivos existan en source y **rechaza** `WEB-INF/classes/models/` dentro del WAR.

## 2. Jars por perfil (`${lib.excludes}`)

Default Core: placeholder `WEB-INF/lib/__no_lib_exclude__/**` (conserva firebase, POI, Meili, spatial, webflux, swagger).

Todos los WARs: `WEB-INF/lib/tomcat-embed-*.jar` (Tomcat externo).

ACS y Traffic (`acs-war`, `acs-staging-war`, `traffic-war`, `traffic-staging-war`): `${lib.excludes.satellite}` más `sshd`, `snmp4j`, `swagger-ui`.

Gateway (`oltgateway-war`, `oltgateway-staging-war`): `${lib.excludes.satellite}` **sin** sshd / snmp4j / swagger-ui.

Los perfiles satélite (`application-traffic|acs|oltgateway.properties`) fijan `hibernate.dialect=org.hibernate.dialect.MySQL57Dialect`. Sin eso heredan el dialecto spatial de `application.properties` y fallan al arrancar (CNFE `MySQL56InnoDBSpatialDialect`) porque el jar ya no va en el WAR.

`${lib.excludes.satellite}` (tras el primer package): firebase/gRPC/GCP (incl. `google-api-services-*`, `gapic-google-*`), POI/xmlbeans/commons-math3, Meili, JTS, hibernate-spatial, `postgresql-*` (solo lo usaba spatial), `spring-webflux`, `reactor-netty`, `kotlinx-coroutines-reactor`. Se **conserva** `reactor-core` (Lettuce/Redis).

Los MB de la tabla se midieron con ese set salvo `postgresql-*` / `google-api-services-*` / `gapic-google-*` (~1.6 MB extra que salen en el próximo package de satélites).

## 3. Deploy selectivo (staging)

`--only` gana siempre. Sin flag: `git diff --name-only HEAD`. Árbol limpio o solo docs → error + `--only`.

| Rutas | WAR |
|-------|-----|
| `**/oltgateway/**`, `application-oltgateway.properties` | Gateway |
| `**/acs/**`, `application-acs.properties` | ACS |
| `**/traffic/**`, `application-traffic.properties` | Traffic |
| `**/wispadmin/**`, observability/netdiag/servicehealth, `application-staging.properties` | Core |
| `**/events/**`, `pom.xml`, `application-prod.properties`, `application.properties` | los 4 |

Ejemplo: fix solo Gateway → `package` de `oltgateway-staging-war` + ~72 MB + health Gateway, no los 4 WAR.

Aislamiento JVM: [staging-tomcat-isolation-war-selectivo.md](./staging-tomcat-isolation-war-selectivo.md).

## Verificación

```bash
sh mvnw -Dtest=WarSubsystemPackagingTest,DeployEnvScriptTest,DeploySelectWarsScriptTest,DeployStagingIsolationScriptsTest,ApplicationProdDatasourceHostTest,FaceModelFileResolverTest test
VERIFY_WAR=target/ispadmin-staging.war bash scripts/verify-djl-war.sh
```

## Referencias

- Paths (no secretos): [vps-secrets-management.md](./vps-secrets-management.md)
- DJL en `tomcat/lib`: [deploy-flow.md](./deploy-flow.md)
- Resolver FS: `FaceModelFileResolver` / `FaceModelFileResolverTest`
