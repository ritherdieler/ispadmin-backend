package com.dscorp.wispadmin.wispadmin.requestbody

import com.fasterxml.jackson.annotation.JsonInclude

data class WhatsAppMessageContext(
    val message_id: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class WhatsAppMediaIdPayload(
    val id: String,
    val caption: String? = null
)

/** Meta Cloud API audio object: only id (or link). Caption is not allowed. Optional voice marks a voice note. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class WhatsAppAudioPayload(
    val id: String,
    val voice: Boolean? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
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
    val audio: WhatsAppAudioPayload? = null,
    val biz_opaque_callback_data: String? = null
)
