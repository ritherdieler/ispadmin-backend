#!/usr/bin/env bash
set -euo pipefail

if [ -z "${CATALINA_HOME:-}" ]; then
  echo "Define CATALINA_HOME antes de ejecutar este script."
  echo "Ejemplo: export CATALINA_HOME=/opt/tomcat"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TOMCAT_LIB_SRC="$PROJECT_DIR/target/tomcat-lib"

if [ ! -d "$TOMCAT_LIB_SRC" ]; then
  echo "No existe $TOMCAT_LIB_SRC"
  echo "Compila primero: bash mvnw clean package -DskipTests -Ddjl.linux"
  exit 1
fi

mkdir -p "$CATALINA_HOME/lib"
cp -v "$TOMCAT_LIB_SRC"/*.jar "$CATALINA_HOME/lib/"

SETENV="$CATALINA_HOME/bin/setenv.sh"
if [ ! -f "$SETENV" ]; then
  touch "$SETENV"
  chmod +x "$SETENV"
fi

if ! grep -q "ai.djl.pytorch.native_helper" "$SETENV" 2>/dev/null; then
  cat >> "$SETENV" <<'EOF'

export CATALINA_OPTS="$CATALINA_OPTS -Dai.djl.pytorch.native_helper=com.dscorp.wispadmin.wispadmin.util.PytorchNativeHelper"
export CATALINA_OPTS="$CATALINA_OPTS -DPYTORCH_VERSION=2.7.1 -DPYTORCH_FLAVOR=cpu"
EOF
  echo "Actualizado $SETENV"
else
  echo "setenv.sh ya contiene ai.djl.pytorch.native_helper"
fi

echo "JARs DJL copiados a $CATALINA_HOME/lib"
echo "Reinicia Tomcat: $CATALINA_HOME/bin/shutdown.sh && $CATALINA_HOME/bin/startup.sh"
