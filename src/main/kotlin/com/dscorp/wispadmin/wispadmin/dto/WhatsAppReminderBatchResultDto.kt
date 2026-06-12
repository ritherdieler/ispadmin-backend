package com.dscorp.wispadmin.wispadmin.dto

data class WhatsAppReminderBatchResultDto (
    val requestedLimit: Int,
    val candidates: Int,
    val sent: Int,
    val skipped: Int,
    val failed: Int,
    val details: List<WhatsAppReminderResultDto>
)

data class WhatsAppReminderResultDto(
    val paymentId: Int,
    val subscriptionId: Int?,
    val phone: String?,
    val status: String,
    val reason: String?
)