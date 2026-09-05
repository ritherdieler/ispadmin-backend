# DJL models packaged in WAR (histórico)

**Actualización 2026-09-05:** los tres archivos de `src/main/resources/models/` **ya no van en el WAR**. Prod/staging Core los lee desde `/opt/gigafiber/models/`. Detalle y tamaños: [wars-adelgazados-models-fs.md](./wars-adelgazados-models-fs.md).

Build original (modelos *dentro* del WAR): `bash mvnw clean package -DskipTests` (2026-06-16).

## Changes (2026-06)

- Moved `ultranet.zip` and `face_feature.zip` to `src/main/resources/models/`.
- Added `FaceModelFileResolver` to resolve `classpath:` models into temp files for DJL.
- Updated `FacePhotoPreprocessorService` and `FacePhotoDescriptorService` to use the resolver.
- Dev/local (`application-dev.properties`) sigue en classpath:
  - `face.login.djl-model-path=classpath:models/face_feature.zip`
  - `face.login.djl-detector-model-path=classpath:models/ultranet.zip`

Prod/staging:

- `face.login.djl-model-path=/opt/gigafiber/models/face_feature.zip`
- `face.login.djl-detector-model-path=/opt/gigafiber/models/ultranet.zip`
- `face.embedding.model-path=/opt/gigafiber/models/arcface_w600k_mbf.onnx`

## WAR verification

`scripts/verify-djl-war.sh` exige los modelos en source y **falla** si el WAR contiene `WEB-INF/classes/models/`.

## Runtime verification

With dev profile:

- `Detector facial DJL ultranet listo.`
- `Motor facial DJL listo para generar descriptores.`
- `POST /ispadmin/api/face-data/photo/check` returns HTTP 200

## Deploy note

`./scripts/deploy.sh --deploy` / `--setup` hace rsync de `src/main/resources/models/` a `/opt/gigafiber/models/` y monta ese directorio en Tomcat.

Build the WAR for Linux Tomcat from macOS with:

```bash
bash mvnw clean package -DskipTests -Ddjl.linux
```

For ARM Debian servers:

```bash
bash mvnw clean package -DskipTests -Ddjl.linux.aarch64
```

Building directly on a Linux server auto-selects the native classifier from `uname -m`.

DJL JARs live in the Docker image `lib/` — see `.agent-docs/deploy-flow.md`.

Local macOS development uses the matching `osx-aarch64` or `osx-x86_64` native profile automatically.
