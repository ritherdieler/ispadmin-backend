package com.dscorp.wispadmin.wispadmin.acsclient

import com.fasterxml.jackson.databind.ObjectMapper

data class CoreCpeProvisionRequest(
    val sn: String,
    val uniqueExternalId: String? = null,
    val onuType: String? = null,
    val ip: String? = null,
    val ipSegment: String? = null,
    val wanVlanId: Int = 1,
    val wifiSsid24: String? = null,
    val wifiPassword24: String? = null,
    val wifiSsid5: String? = null,
    val wifiPassword5: String? = null,
    val pppoeUsername: String? = null,
    val pppoePassword: String? = null,
)

data class CoreCpeProvisionResponse(
    val sn: String = "",
    val status: String = "NA",
    val message: String? = null,
    val deviceId: String? = null,
)

data class CoreCpeAccessLayout(
    val sn: String = "",
    val productClass: String? = null,
    val connectionRequestUrl: String? = null,
    val lastInformAt: String? = null,
    val wanIpPath: String? = null,
    val wanPppPath: String? = null,
    val hasPppPath: Boolean = false,
    val wanIpSharesPppSlot: Boolean = false,
)

enum class CoreOnboardingV2ContactState { WAITING, READY }

data class CoreOnboardingV2ContactRequest(
    val operationId: String,
    val sn: String,
)

data class CoreOnboardingV2ContactResponse(
    val state: CoreOnboardingV2ContactState,
    val deviceId: String? = null,
    val model: String? = null,
    val firmware: String? = null,
    val lastInformAt: String? = null,
)

data class CoreOnboardingV2InternetRequest(
    val operationId: String,
    val sn: String,
    val deviceId: String,
    val model: String,
    val firmware: String,
    val username: String,
    val password: String,
    val vlan: Int,
)

data class CoreOnboardingV2InternetCompensateRequest(
    val operationId: String,
    val sn: String,
    val deviceId: String,
    val model: String,
    val firmware: String,
)

data class CoreOnboardingV2TaskResponse(
    val operationId: String,
    val action: String,
    val taskId: String,
    val status: String,
)

data class CoreOnboardingV2InternetStatusRequest(
    val operationId: String, val sn: String, val deviceId: String, val model: String, val firmware: String,
)

data class CoreOnboardingV2InternetStatusResponse(val state: String, val taskId: String)

data class CoreOnboardingV2WifiRequest(
    val operationId: String, val sn: String, val deviceId: String, val model: String, val firmware: String,
    val ssid24: String, val passphrase24: String, val ssid5: String, val passphrase5: String,
)
data class CoreOnboardingV2WifiCompensateRequest(val operationId: String, val sn: String, val deviceId: String, val model: String, val firmware: String)

class AcsCpeCoreClient(
    private val http: AcsHttpClient,
    private val objectMapper: ObjectMapper,
) {
    fun provision(request: CoreCpeProvisionRequest): CoreCpeProvisionResponse {
        val response = http.postJson("/api/acs/v1/cpe/provision", objectMapper.writeValueAsString(request))
        val body = response.body ?: throw IllegalStateException("Empty ACS provision response")
        return objectMapper.readValue(body, CoreCpeProvisionResponse::class.java)
    }

    fun accessLayout(sn: String): CoreCpeAccessLayout {
        val encoded = java.net.URLEncoder.encode(sn, Charsets.UTF_8).replace("+", "%20")
        val response = http.getJson("/api/acs/v1/cpe/$encoded/access-layout")
        val body = response.body ?: throw IllegalStateException("Empty ACS access-layout response")
        return objectMapper.readValue(body, CoreCpeAccessLayout::class.java)
    }

    fun onboardingV2Contact(request: CoreOnboardingV2ContactRequest): CoreOnboardingV2ContactResponse {
        val response = http.postJson("/api/acs/v1/onboarding-v2/contact", objectMapper.writeValueAsString(request))
        val body = response.body ?: throw IllegalStateException("Empty ACS v2 contact response")
        return objectMapper.readValue(body, CoreOnboardingV2ContactResponse::class.java)
    }

    fun enqueueOnboardingV2Internet(request: CoreOnboardingV2InternetRequest): CoreOnboardingV2TaskResponse {
        val response = http.postJson("/api/acs/v1/onboarding-v2/internet", objectMapper.writeValueAsString(request))
        val body = response.body ?: throw IllegalStateException("Empty ACS v2 internet response")
        return objectMapper.readValue(body, CoreOnboardingV2TaskResponse::class.java)
    }

    fun compensateOnboardingV2Internet(request: CoreOnboardingV2InternetCompensateRequest): CoreOnboardingV2TaskResponse {
        val response = http.postJson("/api/acs/v1/onboarding-v2/internet/compensate", objectMapper.writeValueAsString(request))
        val body = response.body ?: throw IllegalStateException("Empty ACS v2 internet compensation response")
        return objectMapper.readValue(body, CoreOnboardingV2TaskResponse::class.java)
    }

    fun onboardingV2InternetStatus(request: CoreOnboardingV2InternetStatusRequest, compensation: Boolean): CoreOnboardingV2InternetStatusResponse {
        val suffix = if (compensation) "/internet/compensate/status" else "/internet/status"
        val response = http.postJson("/api/acs/v1/onboarding-v2$suffix", objectMapper.writeValueAsString(request))
        val body = response.body ?: throw IllegalStateException("Empty ACS v2 internet status response")
        return objectMapper.readValue(body, CoreOnboardingV2InternetStatusResponse::class.java)
    }

    fun enqueueOnboardingV2Wifi(request: CoreOnboardingV2WifiRequest): CoreOnboardingV2TaskResponse = task("/wifi", request)
    fun compensateOnboardingV2Wifi(request: CoreOnboardingV2WifiCompensateRequest): CoreOnboardingV2TaskResponse = task("/wifi/compensate", request)
    fun onboardingV2WifiStatus(request: CoreOnboardingV2WifiCompensateRequest, compensation: Boolean): CoreOnboardingV2InternetStatusResponse {
        val suffix = if (compensation) "/wifi/compensate/status" else "/wifi/status"
        val response = http.postJson("/api/acs/v1/onboarding-v2$suffix", objectMapper.writeValueAsString(request))
        val body = response.body ?: throw IllegalStateException("Empty ACS v2 Wi-Fi status response")
        return objectMapper.readValue(body, CoreOnboardingV2InternetStatusResponse::class.java)
    }

    private fun task(path: String, request: Any): CoreOnboardingV2TaskResponse {
        val response = http.postJson("/api/acs/v1/onboarding-v2$path", objectMapper.writeValueAsString(request))
        val body = response.body ?: throw IllegalStateException("Empty ACS v2 task response")
        return objectMapper.readValue(body, CoreOnboardingV2TaskResponse::class.java)
    }
}
