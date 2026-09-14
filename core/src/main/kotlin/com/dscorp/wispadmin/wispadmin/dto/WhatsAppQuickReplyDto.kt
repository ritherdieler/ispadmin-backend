package com.dscorp.wispadmin.wispadmin.dto

import java.time.LocalDateTime

data class WhatsAppQuickReplyDto(
    val id: Long,
    val title: String,
    val shortcut: String,
    val content: String,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)

data class WhatsAppQuickReplyBody(
    val title: String,
    val shortcut: String,
    val content: String
)
