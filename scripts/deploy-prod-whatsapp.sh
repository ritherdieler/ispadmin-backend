#!/bin/bash
# Plantilla de variables WhatsApp para produccion.
# Copia este archivo en el servidor como /etc/wispadmin/whatsapp.env
# y reemplaza WHATSAPP_ACCESS_TOKEN con el token del usuario Employee.
#
# Habilitar insights Meta (IRREVERSIBLE — ejecutar manualmente en prod, NO en tests):
# curl -X POST "https://graph.facebook.com/v25.0/${WHATSAPP_BUSINESS_ACCOUNT_ID}?is_enabled_for_insights=true" \
#   -H "Authorization: Bearer ${WHATSAPP_ACCESS_TOKEN}"
#
# Suscribir webhooks de gestion en Meta App Dashboard:
# message_template_status_update, message_template_quality_update,
# account_alerts, template_category_update, phone_number_quality_update

export WHATSAPP_PHONE_NUMBER_ID=1187318341136661
export WHATSAPP_BUSINESS_ACCOUNT_ID=27768608566160691
export WHATSAPP_WEBHOOK_VERIFY_TOKEN=gigafiber_whatsapp_verify_2026
export WHATSAPP_ACCESS_TOKEN=PEGAR_TOKEN_EMPLOYEE_AQUI
export WHATSAPP_APP_SECRET=PEGAR_APP_SECRET_AQUI

echo "Variables WhatsApp cargadas para produccion."
echo "Phone Number ID: $WHATSAPP_PHONE_NUMBER_ID (GigaFiberPeru-Mensajes +51 984 224 137)"
echo "Webhook verify token: $WHATSAPP_WEBHOOK_VERIFY_TOKEN"
