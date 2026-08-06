package com.dscorp.wispadmin.wispadmin.dto

data class WhatsAppBatchSendAcceptedDto(
    val campaignId: String,
    val templateCode: String,
    val total: Int,
    val status: String
)

data class WhatsAppBatchSendStatusDto(
    val campaignId: String,
    val templateCode: String,
    val status: String,
    val total: Int,
    val processed: Int,
    val sent: Int,
    val skipped: Int,
    val failed: Int,
    val details: List<WhatsAppMessageResultDto> = emptyList()
)
