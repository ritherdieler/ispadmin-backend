#!/bin/bash

# Script para ejecutar el backend en modo desarrollo
echo "🚀 Iniciando backend en modo DESARROLLO..."
echo "Profile: dev"
echo "Base de datos: ispadmin_dev"
echo "Firebase: ispadmin-dev"
echo "NetDiag: habilitado (NET_DIAG_ENABLED=false para desactivar)"
echo ""

./gradlew :core:bootRun \
  -Dspring-boot.run.jvmArguments="-Djava.net.preferIPv4Stack=true -Dspring.devtools.restart.enabled=false" \
  --args="--spring.profiles.active=dev,local"
