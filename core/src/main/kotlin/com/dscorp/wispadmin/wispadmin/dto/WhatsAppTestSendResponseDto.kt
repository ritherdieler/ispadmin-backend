package com.dscorp.wispadmin.wispadmin.dto

data class WhatsAppTestSendResponseDto(
    val success: Boolean,
    val message: String,
    val recipient: String?,
    val senderPhoneNumberId: String,
    val metaResponse: String,
    val deliveryHint: String
)
