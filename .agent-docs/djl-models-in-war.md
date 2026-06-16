# DJL models packaged in WAR

Build verified: `bash mvnw clean package -DskipTests` (2026-06-16).

## Changes

- Moved `ultranet.zip` and `face_feature.zip` to `src/main/resources/models/`.
- Added `FaceModelFileResolver` to resolve `classpath:` models into temp files for DJL.
- Updated `FacePhotoPreprocessorService` and `FacePhotoDescriptorService` to use the resolver.
- Unified `application-dev.properties` and `application-prod.properties` with:
  - `face.login.djl-model-path=classpath:models/face_feature.zip`
  - `face.login.djl-detector-model-path=classpath:models/ultranet.zip`

## WAR verification

`target/ispadmin.war` includes:

- `WEB-INF/classes/models/face_feature.zip`
- `WEB-INF/classes/models/ultranet.zip`

## Runtime verification

With dev profile:

- `Detector facial DJL ultranet listo.`
- `Motor facial DJL listo para generar descriptores.`
- `POST /ispadmin/api/face-data/photo/check` returns HTTP 200

## Deploy note

Upload `target/ispadmin.war` to Tomcat. External model files under `/opt/ispadmin/models/` are no longer required unless you override the properties with absolute paths.

Build the WAR for Linux Tomcat from macOS with:

```bash
bash mvnw clean package -DskipTests -Ddjl.linux
```

For ARM Debian servers:

```bash
bash mvnw clean package -DskipTests -Ddjl.linux.aarch64
```

Building directly on a Linux server auto-selects the native classifier from `uname -m`.

This bundles `pytorch-native-cpu` and `pytorch-jni` inside the WAR. For Tomcat, also copy `target/tomcat-lib/*.jar` to `$CATALINA_HOME/lib/` — see `.agent-docs/djl-tomcat-debian.md`.

Local macOS development uses the matching `osx-aarch64` or `osx-x86_64` native profile automatically.
