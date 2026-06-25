#!/bin/bash

# Script para ejecutar el backend en modo desarrollo
echo "🚀 Iniciando backend en modo DESARROLLO..."
echo "Profile: dev"
echo "Base de datos: ispadmin_dev"
echo "Firebase: ispadmin-dev"
echo ""

sh mvnw spring-boot:run -Dspring-boot.run.profiles=dev
