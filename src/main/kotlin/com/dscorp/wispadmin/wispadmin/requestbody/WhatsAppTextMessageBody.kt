package com.dscorp.wispadmin.wispadmin.requestbody

data class WhatsAppTextMessageBody(
    val messaging_product: String = "whatsapp",
    val to: String,
    val type: String = "text",
    val text: WhatsAppTextContent,
    val context: WhatsAppMessageContext? = null
)

// Representa el contenido del mensaje de texto que recibira el cliente.
data class WhatsAppTextContent(
    val preview_url: Boolean = false,
    val body: String
)