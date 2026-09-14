package com.dscorp.wispadmin.wispadmin.requestbody

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class WhatsAppTextMessageBody(
    val messaging_product: String = "whatsapp",
    val to: String,
    val type: String = "text",
    val text: WhatsAppTextContent,
    val context: WhatsAppMessageContext? = null,
    val biz_opaque_callback_data: String? = null
)

// Representa el contenido del mensaje de texto que recibira el cliente.
data class WhatsAppTextContent(
    val preview_url: Boolean = false,
    val body: String
)