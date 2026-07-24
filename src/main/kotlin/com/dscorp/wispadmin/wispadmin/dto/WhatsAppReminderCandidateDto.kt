package com.dscorp.wispadmin.wispadmin.dto

data class WhatsAppReminderCandidateDto(
    val paymentId: Int,
    val subscriptionId: Int,
    val clientName: String,
    val phone: String,
    val amount: Double,
    val billingDate: String,
    val alreadySentToday: Boolean
)
