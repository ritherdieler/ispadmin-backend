package com.dscorp.wispadmin.wispadmin.dto

import java.time.LocalDateTime

data class CrmQuickReplyDto(
    val id: Long,
    val title: String,
    val body: String,
    val ownerUserId: Int?,
    val global: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CrmQuickReplyBody(
    val title: String,
    val body: String,
    val global: Boolean = false
)
