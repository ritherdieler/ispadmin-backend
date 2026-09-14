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
}
