#!/bin/bash

# Script para ejecutar el backend en modo producción
echo "🚀 Iniciando backend en modo PRODUCCIÓN..."
echo "Profile: prod"
echo "Base de datos: ispadmin (producción)"
echo "Firebase: ispadmin-687ca"
echo ""
echo "Variables WhatsApp requeridas en el servidor:"
echo "  WHATSAPP_WEBHOOK_VERIFY_TOKEN=gigafiber_whatsapp_verify_2026"
echo "  WHATSAPP_PHONE_NUMBER_ID=1187318341136661"
echo "  WHATSAPP_BUSINESS_ACCOUNT_ID=27768608566160691"
echo "  WHATSAPP_ACCESS_TOKEN=<token Employee desde Meta Business Suite>"
echo "  WHATSAPP_APP_SECRET=<opcional>"
echo ""
echo "Plantilla: deploy-prod-whatsapp.sh"
echo "Webhook prod: https://<tu-dominio-o-ip>/ispadmin/whatsapp/webhook"
echo ""

./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
