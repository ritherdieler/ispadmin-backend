#!/bin/bash

# Script para ejecutar el backend en modo desarrollo
echo "🚀 Iniciando backend en modo DESARROLLO..."
echo "Profile: dev"
echo "Base de datos: ispadmin_dev"
echo "Firebase: ispadmin-dev"
echo "NetDiag: habilitado (NET_DIAG_ENABLED=false para desactivar)"
echo ""

sh mvnw spring-boot:run -Dspring-boot.run.profiles=dev,local -Dspring-boot.run.jvmArguments="-Djava.net.preferIPv4Stack=true -Dspring.devtools.restart.enabled=false"
