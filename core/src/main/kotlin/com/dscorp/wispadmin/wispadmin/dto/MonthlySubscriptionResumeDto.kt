package com.dscorp.wispadmin.wispadmin.dto

import java.util.*

data class MonthlySubscriptionResumeDto(
    val totalActiveSubscriptions: Int,
    val newSubscriptions: Int,
    val cancelledSubscriptions: Int,
    val date: Date
)