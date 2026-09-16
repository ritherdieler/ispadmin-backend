package com.dscorp.wispadmin.traffic.dto

data class SubscriptionLiveReadingDto(
    val subscriptionId: Int,
    val available: Boolean,
    val pppoe: String?,
    val timestamp: String,
    val downloadBps: Long,
    val uploadBps: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val source: String,
)
