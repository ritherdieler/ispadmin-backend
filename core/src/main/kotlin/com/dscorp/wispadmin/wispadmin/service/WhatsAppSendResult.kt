package com.dscorp.wispadmin.wispadmin.service

data class WhatsAppSendResult(
    val success: Boolean,
    val metaResponse: String,
    val metaMessageId: String? = null,
    val recipient: String?,
    val senderPhoneNumberId: String
)
