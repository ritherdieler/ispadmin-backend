package com.dscorp.wispadmin.acs

enum class OnboardingV2ContactState { WAITING, READY }

data class OnboardingV2ContactRequest(
    val operationId: String,
    val sn: String,
)

data class OnboardingV2ContactResponse(
    val state: OnboardingV2ContactState,
    val deviceId: String? = null,
    val model: String? = null,
    val firmware: String? = null,
    val lastInformAt: String? = null,
)
