package com.dscorp.wispadmin.wispadmin.dto

data class CrmRealtimeEventDto(
    val eventId: Long,
    val eventType: String,
    val payload: Map<String, Any?>,
    val createdAt: String
)
