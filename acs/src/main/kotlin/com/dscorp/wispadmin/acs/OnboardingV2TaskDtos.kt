package com.dscorp.wispadmin.acs

data class OnboardingV2InternetRequest(
    val operationId: String,
    val sn: String,
    val deviceId: String,
    val model: String,
    val firmware: String,
    val username: String,
    val password: String,
    val vlan: Int,
    val mode: String = "pppoe",
    val ip: String? = null,
    val subnetMask: String? = null,
    val gateway: String? = null,
    val dns: String? = null,
)

data class OnboardingV2TaskResponse(
    val operationId: String,
    val action: String,
    val taskId: String,
    val status: String,
)

data class OnboardingV2InternetCompensateRequest(
    val operationId: String,
    val sn: String,
    val deviceId: String,
    val model: String,
    val firmware: String,
)

data class OnboardingV2InternetStatusRequest(
    val operationId: String,
    val sn: String,
    val deviceId: String,
    val model: String,
    val firmware: String,
)

data class OnboardingV2InternetStatusResponse(
    val state: String,
    val taskId: String,
    val reason: String? = null,
)

data class OnboardingV2WifiRequest(
    val operationId: String, val sn: String, val deviceId: String, val model: String, val firmware: String,
    val ssid24: String, val passphrase24: String, val ssid5: String, val passphrase5: String,
)

data class OnboardingV2WifiCompensateRequest(
    val operationId: String, val sn: String, val deviceId: String, val model: String, val firmware: String,
)
