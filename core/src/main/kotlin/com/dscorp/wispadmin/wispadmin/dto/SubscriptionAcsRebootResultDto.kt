package com.dscorp.wispadmin.wispadmin.dto

import java.io.Serializable

data class SubscriptionAcsRebootResultDto(
    val subscriptionId: Int,
    val deviceId: String,
    val taskId: String? = null,
    val accepted: Boolean,
    val message: String,
) : Serializable
