#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WAR="${VERIFY_WAR:-$PROJECT_DIR/app/build/libs/ispadmin.war}"
TOMCAT_LIB="${TOMCAT_LIB_SRC:-$PROJECT_DIR/app/build/tomcat-lib}"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

if [ ! -f "$WAR" ]; then
  fail "No existe $WAR. Compila primero: ./gradlew :app:war :app:tomcatLibs -Pdjl.linux"
fi

MODELS_DIR="$PROJECT_DIR/core/src/main/resources/models"
for model in face_feature.zip ultranet.zip arcface_w600k_mbf.onnx; do
  if [ ! -f "$MODELS_DIR/$model" ]; then
    fail "Falta $MODELS_DIR/$model. Restaura los modelos DJL antes de compilar/desplegar."
  fi
done

WAR_LIST="$(jar tf "$WAR")"

for model in face_feature.zip ultranet.zip arcface_w600k_mbf.onnx; do
  if grep -q "WEB-INF/classes/models/$model" <<< "$WAR_LIST"; then
    fail "El WAR contiene WEB-INF/classes/models/$model. Los modelos viven en /opt/gigafiber/models (rsync de deploy), no dentro del WAR."
  fi
done

if grep -q "WEB-INF/classes/models/" <<< "$WAR_LIST"; then
  fail "El WAR contiene WEB-INF/classes/models/. packingExcludes debe omitir WEB-INF/classes/models/**."
fi

if grep -q "WEB-INF/lib/.*\\(api-${DJL_VERSION:-0.36.0}\\|pytorch-engine\\|pytorch-jni\\|pytorch-native-cpu\\|onnxruntime-engine\\|onnxruntime-\\).*\\.jar" <<< "$WAR_LIST"; then
  echo "$WAR_LIST" | grep -E "WEB-INF/lib/.*(api-${DJL_VERSION:-0.36.0}|pytorch-engine|pytorch-jni|pytorch-native-cpu|onnxruntime-engine|onnxruntime-).*\.jar" >&2
  fail "El WAR contiene jars DJL/PyTorch/ONNX. En Tomcat deben vivir solo en CATALINA_HOME/lib para permitir redeploy por Manager sin choque de classloaders."
fi

if grep -q "osx-aarch64" <<< "$WAR_LIST"; then
  fail "El WAR contiene osx-aarch64. Para Debian x86_64 recompila con: ./gradlew :app:war :app:tomcatLibs -Pdjl.linux"
fi

if grep -q "WEB-INF/classes/com/dscorp/wispadmin/wispadmin/util/PytorchNativeHelper.class" <<< "$WAR_LIST"; then
  fail "El WAR contiene PytorchNativeHelper. Esa clase debe estar solo en CATALINA_HOME/lib/ispadmin-djl-native-helper.jar para evitar classloader nativo por redeploy."
fi

CORE_JAR="$(mktemp)"
if ! unzip -p "$WAR" WEB-INF/lib/core.jar > "$CORE_JAR"; then
  rm -f "$CORE_JAR"
  fail "El WAR no contiene WEB-INF/lib/core.jar"
fi
if jar tf "$CORE_JAR" | grep -q "PytorchNativeHelper"; then
  rm -f "$CORE_JAR"
  fail "WEB-INF/lib/core.jar contiene PytorchNativeHelper; esa clase debe vivir solo en CATALINA_HOME/lib/ispadmin-djl-native-helper.jar"
fi
rm -f "$CORE_JAR"

if [ ! -d "$TOMCAT_LIB" ] || [ -z "$(find "$TOMCAT_LIB" -maxdepth 1 -name '*.jar' -print -quit)" ]; then
  fail "$TOMCAT_LIB no existe o no contiene JARs. Recompila con: ./gradlew :app:war :app:tomcatLibs -Pdjl.linux"
fi

if ! find "$TOMCAT_LIB" -maxdepth 1 -name "pytorch-native-cpu-*-linux-x86_64.jar" -print -quit | grep -q .; then
  fail "Falta pytorch-native-cpu linux-x86_64 en $TOMCAT_LIB."
fi

if [ ! -f "$TOMCAT_LIB/ispadmin-djl-native-helper.jar" ]; then
  fail "Falta $TOMCAT_LIB/ispadmin-djl-native-helper.jar."
fi

if ! find "$TOMCAT_LIB" -maxdepth 1 -name "slf4j-api-*.jar" -print -quit | grep -q .; then
  fail "Falta slf4j-api en $TOMCAT_LIB. DJL en CATALINA_HOME/lib lo necesita en el classloader común."
fi

for required in "gson-*.jar" "jna-*.jar" "commons-compress-*.jar"; do
  if ! find "$TOMCAT_LIB" -maxdepth 1 -name "$required" -print -quit | grep -q .; then
    fail "Falta $required en $TOMCAT_LIB. DJL en CATALINA_HOME/lib necesita sus dependencias transitivas en el classloader común."
  fi
done

if ! find "$TOMCAT_LIB" -maxdepth 1 -name "onnxruntime-engine-*.jar" -print -quit | grep -q .; then
  fail "Falta onnxruntime-engine en $TOMCAT_LIB."
fi

if ! find "$TOMCAT_LIB" -maxdepth 1 -name "onnxruntime-*.jar" -print -quit | grep -q .; then
  fail "Falta com.microsoft.onnxruntime:onnxruntime en $TOMCAT_LIB."
fi

echo "OK: WAR sin DJL/PyTorch/ONNX, sin modelos faciales embebidos, nativos Linux en tomcat-lib."
