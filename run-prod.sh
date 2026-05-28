#!/bin/bash

# Script para ejecutar el backend en modo producción
echo "🚀 Iniciando backend en modo PRODUCCIÓN..."
echo "Profile: prod"
echo "Base de datos: ispadmin (producción)"
echo "Firebase: ispadmin-687ca"
echo ""

./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
