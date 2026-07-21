#!/bin/bash
# Plantilla de variables WhatsApp para produccion.
# Copia este archivo en el servidor como /etc/wispadmin/whatsapp.env
# y reemplaza WHATSAPP_ACCESS_TOKEN con el token del usuario Employee.

export WHATSAPP_PHONE_NUMBER_ID=1187318341136661
export WHATSAPP_BUSINESS_ACCOUNT_ID=27768608566160691
export WHATSAPP_WEBHOOK_VERIFY_TOKEN=gigafiber_whatsapp_verify_2026
export WHATSAPP_ACCESS_TOKEN=PEGAR_TOKEN_EMPLOYEE_AQUI
# Opcional: App Secret de Meta para validar firma del webhook POST
# export WHATSAPP_APP_SECRET=

echo "Variables WhatsApp cargadas para produccion."
echo "Phone Number ID: $WHATSAPP_PHONE_NUMBER_ID (GigaFiberPeru-Mensajes +51 984 224 137)"
echo "Webhook verify token: $WHATSAPP_WEBHOOK_VERIFY_TOKEN"
