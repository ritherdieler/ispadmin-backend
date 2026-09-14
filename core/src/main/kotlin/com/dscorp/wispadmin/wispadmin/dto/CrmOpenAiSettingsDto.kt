package com.dscorp.wispadmin.wispadmin.dto

import java.time.LocalDateTime

data class CrmOpenAiSettingsDto(
    val configured: Boolean,
    val maskedApiKey: String?,
    val model: String,
    val enabled: Boolean,
    val updatedAt: LocalDateTime?,
    val updatedBy: String?
)

data class CrmOpenAiSettingsUpdateBody(
    val apiKey: String? = null,
    val model: String? = null,
    val enabled: Boolean? = null
)

data class CrmOpenAiTestResultDto(
    val success: Boolean,
    val message: String,
    val model: String? = null
)

data class CrmSuggestReplyDto(
    val suggestion: String,
    val source: String,
    val requiresConfirmation: Boolean = true
)

data class CrmBotControlBody(
    val note: String? = null
)
