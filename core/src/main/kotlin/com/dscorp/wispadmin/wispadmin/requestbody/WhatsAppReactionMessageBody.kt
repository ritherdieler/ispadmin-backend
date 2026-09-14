package com.dscorp.wispadmin.wispadmin.requestbody

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class WhatsAppReactionContent(
    val message_id: String,
    val emoji: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class WhatsAppReactionMessageBody(
    val messaging_product: String = "whatsapp",
    val recipient_type: String = "individual",
    val to: String,
    val type: String = "reaction",
    val reaction: WhatsAppReactionContent,
)
