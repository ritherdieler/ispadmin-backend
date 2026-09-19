package com.dscorp.wispadmin.wispadmin.dto

enum class AcsLinkStatus {
    LINKED,
    SKIP_NONE,
    SKIP_AMBIGUOUS,
    SKIP_NOT_ACTIVE,
    SKIP_DEVICE_NOT_FOUND,
}

data class SubscriptionAcsLinkRequest(
    val deviceId: String,
    val dryRun: Boolean = false,
)

data class SubscriptionAcsLinkResult(
    val status: AcsLinkStatus,
    val deviceId: String,
    val subscriptionId: Int? = null,
    val message: String? = null,
)

data class AcsGhostDevice(
    val deviceId: String,
    val serialNumber: String? = null,
    val lastInform: String? = null,
    val suffix: String? = null,
)
