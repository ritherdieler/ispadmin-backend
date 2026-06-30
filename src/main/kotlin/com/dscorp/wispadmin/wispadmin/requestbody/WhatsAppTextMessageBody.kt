package com.dscorp.wispadmin.wispadmin.requestbody

// Representa el cuerpo principal que WhatsApp Cloud API espera para enviar un mensaje de texto.
data class WhatsAppTextMessageBody(
    val messaging_product: String = "whatsapp",
    val to: String,
    val type: String = "text",
    val text: WhatsAppTextContent
)

// Representa el contenido del mensaje de texto que recibira el cliente.
data class WhatsAppTextContent(
    val preview_url: Boolean = false,
    val body: String
)