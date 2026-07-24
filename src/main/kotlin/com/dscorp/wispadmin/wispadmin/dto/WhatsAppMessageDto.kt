package com.dscorp.wispadmin.wispadmin.dto

data class WhatsAppTemplateOptionDto(
    val code: String,
    val metaName: String,
    val label: String,
    val description: String,
    val targetType: String,
    val showAmount: Boolean,
    val showBillingDate: Boolean,
    val showPaymentDate: Boolean,
    val showInstallationDate: Boolean
)

data class WhatsAppMessageCandidateDto(
    val targetType: String,
    val targetId: Int,
    val paymentId: Int?,
    val subscriptionId: Int,
    val clientName: String,
    val phone: String,
    val amount: Double?,
    val billingDate: String?,
    val paymentDate: String?,
    val installationDate: String?,
    val alreadySentToday: Boolean
)

data class WhatsAppMessageBatchResultDto(
    val templateCode: String,
    val requestedLimit: Int,
    val candidates: Int,
    val sent: Int,
    val skipped: Int,
    val failed: Int,
    val details: List<WhatsAppMessageResultDto>
)

data class WhatsAppMessageResultDto(
    val targetId: Int,
    val targetType: String,
    val paymentId: Int?,
    val subscriptionId: Int?,
    val phone: String?,
    val status: String,
    val reason: String?
)
