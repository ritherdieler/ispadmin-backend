package com.dscorp.wispadmin.wispadmin.requestbody

data class WhatsAppSendMessagesRequest(
    val templateCode: String,
    val targetIds: List<Int> = emptyList()
)
