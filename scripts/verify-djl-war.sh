#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WAR="${VERIFY_WAR:-$PROJECT_DIR/target/ispadmin.war}"
TOMCAT_LIB="$PROJECT_DIR/target/tomcat-lib"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

if [ ! -f "$WAR" ]; then
  fail "No existe $WAR. Compila primero: bash mvnw clean package -DskipTests -Ddjl.linux"
fi

MODELS_DIR="$PROJECT_DIR/src/main/resources/models"
for model in face_feature.zip ultranet.zip arcface_w600k_mbf.onnx; do
  if [ ! -f "$MODELS_DIR/$model" ]; then
    fail "Falta $MODELS_DIR/$model. Restaura los modelos DJL antes de compilar/desplegar."
  fi
done

WAR_LIST="$(jar tf "$WAR")"

for model in face_feature.zip ultranet.zip arcface_w600k_mbf.onnx; do
  if ! grep -q "WEB-INF/classes/models/$model" <<< "$WAR_LIST"; then
    fail "El WAR no contiene WEB-INF/classes/models/$model. Recompila tras restaurar src/main/resources/models/$model"
  fi
done

if grep -q "WEB-INF/lib/.*\\(api-${DJL_VERSION:-0.36.0}\\|pytorch-engine\\|pytorch-jni\\|pytorch-native-cpu\\|onnxruntime-engine\\|onnxruntime-\\).*\\.jar" <<< "$WAR_LIST"; then
  echo "$WAR_LIST" | grep -E "WEB-INF/lib/.*(api-${DJL_VERSION:-0.36.0}|pytorch-engine|pytorch-jni|pytorch-native-cpu|onnxruntime-engine|onnxruntime-).*\.jar" >&2
  fail "El WAR contiene jars DJL/PyTorch/ONNX. En Tomcat deben vivir solo en CATALINA_HOME/lib para permitir redeploy por Manager sin choque de classloaders."
fi

if grep -q "osx-aarch64" <<< "$WAR_LIST"; then
  fail "El WAR contiene osx-aarch64. Para Debian x86_64 recompila con: bash mvnw clean package -DskipTests -Ddjl.linux"
fi

if grep -q "WEB-INF/classes/com/dscorp/wispadmin/wispadmin/util/PytorchNativeHelper.class" <<< "$WAR_LIST"; then
  fail "El WAR contiene PytorchNativeHelper. Esa clase debe estar solo en CATALINA_HOME/lib/ispadmin-djl-native-helper.jar para evitar classloader nativo por redeploy."
fi

if [ ! -d "$TOMCAT_LIB" ] || [ -z "$(find "$TOMCAT_LIB" -maxdepth 1 -name '*.jar' -print -quit)" ]; then
  fail "$TOMCAT_LIB no existe o no contiene JARs. Recompila con: bash mvnw clean package -DskipTests -Ddjl.linux"
fi

if ! find "$TOMCAT_LIB" -maxdepth 1 -name "pytorch-native-cpu-*-linux-x86_64.jar" -print -quit | grep -q .; then
  fail "Falta pytorch-native-cpu linux-x86_64 en target/tomcat-lib."
fi

if [ ! -f "$TOMCAT_LIB/ispadmin-djl-native-helper.jar" ]; then
  fail "Falta $TOMCAT_LIB/ispadmin-djl-native-helper.jar."
fi

if ! find "$TOMCAT_LIB" -maxdepth 1 -name "slf4j-api-*.jar" -print -quit | grep -q .; then
  fail "Falta slf4j-api en target/tomcat-lib. DJL en CATALINA_HOME/lib lo necesita en el classloader común."
fi

for required in "gson-*.jar" "jna-*.jar" "commons-compress-*.jar"; do
  if ! find "$TOMCAT_LIB" -maxdepth 1 -name "$required" -print -quit | grep -q .; then
    fail "Falta $required en target/tomcat-lib. DJL en CATALINA_HOME/lib necesita sus dependencias transitivas en el classloader común."
  fi
done

if ! find "$TOMCAT_LIB" -maxdepth 1 -name "onnxruntime-engine-*.jar" -print -quit | grep -q .; then
  fail "Falta onnxruntime-engine en target/tomcat-lib."
fi

if ! find "$TOMCAT_LIB" -maxdepth 1 -name "onnxruntime-*.jar" -print -quit | grep -q .; then
  fail "Falta com.microsoft.onnxruntime:onnxruntime en target/tomcat-lib."
fi

echo "OK: WAR sin DJL/PyTorch/ONNX, modelos faciales empaquetados y target/tomcat-lib contiene DJL/PyTorch/ONNX para Debian x86_64."
echo "WAR models:"
jar tf "$WAR" | grep "WEB-INF/classes/models/" || echo "NONE"
echo "WAR DJL/PyTorch/ONNX (must be NONE):"
jar tf "$WAR" | grep -E "WEB-INF/lib/(api-${DJL_VERSION:-0.36.0}|pytorch-engine|pytorch-jni|pytorch-native-cpu|onnxruntime-engine|onnxruntime-)" || echo "NONE"
echo "WAR native helper:"
jar tf "$WAR" | grep "WEB-INF/classes/com/dscorp/wispadmin/wispadmin/util/PytorchNativeHelper.class" || echo "NONE"
echo "Tomcat lib:"
ls -1 "$TOMCAT_LIB" | grep -E "^(api|pytorch|onnxruntime|ispadmin-djl|slf4j-api|gson|jna|commons-compress)"
