# DJL PyTorch en Tomcat (Debian)

Build verified: `bash mvnw clean package -DskipTests -Ddjl.linux` (2026-06-16).

## Problema

`Failed to load PyTorch native library` en Tomcat suele deberse a:

1. WAR compilado **sin** el jar nativo de Linux (p. ej. build en Mac sin `-Ddjl.linux`, o build en Debian sin perfil activo).
2. Arquitectura incorrecta (`linux-x86_64` vs `linux-aarch64`).
3. Classloader de Tomcat: las `.so` deben cargarse desde el classloader del sistema.

## Build del WAR

### Desde macOS (servidor x86_64)

```bash
bash mvnw clean package -DskipTests -Ddjl.linux
```

### Desde macOS (servidor ARM / aarch64)

```bash
bash mvnw clean package -DskipTests -Ddjl.linux.aarch64
```

### Directamente en el servidor Debian

```bash
bash mvnw clean package -DskipTests
```

En Linux el perfil nativo se activa solo según `uname -m` (`amd64` → `linux-x86_64`, `aarch64` → `linux-aarch64`).

## Verificar el WAR antes de subirlo

```bash
jar tf target/ispadmin.war | grep pytorch-native
```

Debe aparecer **exactamente uno** de:

- `WEB-INF/lib/pytorch-native-cpu-2.7.1-linux-x86_64.jar`
- `WEB-INF/lib/pytorch-native-cpu-2.7.1-linux-aarch64.jar`

Si no aparece ninguno, el WAR no lleva PyTorch nativo y fallará en Tomcat.

Comprobar arquitectura del servidor:

```bash
uname -m
```

| `uname -m`   | Jar requerido      |
|--------------|--------------------|
| x86_64       | linux-x86_64       |
| aarch64      | linux-aarch64      |

## Despliegue en Tomcat (obligatorio para natives)

Tomcat carga librerías nativas una sola vez por JVM. Los jars DJL deben ir en **`$CATALINA_HOME/lib`**, no solo dentro del WAR.

Tras `mvn package`, copiar:

```bash
cp target/tomcat-lib/*.jar $CATALINA_HOME/lib/
```

Archivos mínimos en `lib/`:

- `api-0.36.0.jar`
- `pytorch-engine-0.36.0.jar`
- `pytorch-jni-2.7.1-0.36.0.jar`
- `pytorch-native-cpu-2.7.1-linux-x86_64.jar` (o `linux-aarch64`)

En `$CATALINA_HOME/bin/setenv.sh`:

```bash
export CATALINA_OPTS="$CATALINA_OPTS -Dai.djl.pytorch.native_helper=ai.djl.pytorch.jni.NativeHelper"
export CATALINA_OPTS="$CATALINA_OPTS -DPYTORCH_VERSION=2.7.1 -DPYTORCH_FLAVOR=cpu"
```

Reiniciar Tomcat por completo (no solo redeploy):

```bash
$CATALINA_HOME/bin/shutdown.sh
$CATALINA_HOME/bin/startup.sh
```

## Logs de arranque

Buscar en `catalina.out`:

```
DJL bootstrap: os=Linux arch=amd64 native_helper=ai.djl.pytorch.jni.NativeHelper pytorch_native_jars=pytorch-native-cpu-2.7.1-linux-x86_64.jar
Detector facial DJL ultranet listo.
Motor facial DJL listo para generar descriptores.
```

Si aparece `pytorch_native_jars=NONE`, el WAR no incluye el jar nativo correcto.

## Modelos faciales

Los modelos siguen en `WEB-INF/classes/models/`:

- `ultranet.zip`
- `face_feature.zip`

Override opcional en prod con rutas absolutas en el servidor.

## GLIBC en Debian antiguo

PyTorch 2.7.1 requiere GLIBC reciente. En Debian muy viejo, si `ldd` sobre las `.so` muestra versiones GLIBC no encontradas, hay que actualizar el SO o bajar a PyTorch 2.5.1 con build `precxx11` (cambio mayor en dependencias).
