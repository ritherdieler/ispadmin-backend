package com.dscorp.wispadmin.wispadmin.service

data class WhatsAppSendResult(
    val success: Boolean,
    val metaResponse: String,
    val recipient: String?,
    val senderPhoneNumberId: String
)
