package com.dscorp.wispadmin.wispadmin.requestbody

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class WhatsAppReactMessageBody @JsonCreator constructor(
    @JsonProperty("emoji") val emoji: String = "",
)

data class WhatsAppEditMessageBody @JsonCreator constructor(
    @JsonProperty("text") val text: String? = null,
    @JsonProperty("body") val body: String? = null,
) {
    @get:JsonIgnore
    val resolvedText: String
        get() = text?.takeIf { it.isNotBlank() } ?: body.orEmpty()
}
