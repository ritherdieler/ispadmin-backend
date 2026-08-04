package com.dscorp.wispadmin.wispadmin.requestbody

import com.fasterxml.jackson.annotation.JsonInclude

data class WhatsAppMessageContext(
    val message_id: String
)

data class WhatsAppMediaIdPayload(
    val id: String,
    val caption: String? = null
)

data class WhatsAppDocumentPayload(
    val id: String,
    val caption: String? = null,
    val filename: String? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class WhatsAppMediaMessageBody(
    val messaging_product: String = "whatsapp",
    val to: String,
    val type: String,
    val context: WhatsAppMessageContext? = null,
    val image: WhatsAppMediaIdPayload? = null,
    val document: WhatsAppDocumentPayload? = null,
    val audio: WhatsAppMediaIdPayload? = null,
    val biz_opaque_callback_data: String? = null
)
