package com.dscorp.wispadmin.wispadmin.requestbody

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class WhatsAppConversationReplyBody @JsonCreator constructor(
    @JsonProperty("text") val text: String = "",
    @JsonProperty("replyToMessageId") val replyToMessageId: String? = null
)

data class WhatsAppConversationTemplateBody @JsonCreator constructor(
    @JsonProperty("templateCode") val templateCode: String = ""
)
