package com.dscorp.wispadmin.oltgateway.client

import com.dscorp.wispadmin.oltgateway.dto.CpeProvisionStatus
import com.dscorp.wispadmin.oltgateway.dto.CpeTelemetryDto
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestClientException

data class AcsCpeProvisionRequest(
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
)

data class AcsCpeProvisionResponse(
    val sn: String,
    val status: CpeProvisionStatus,
    val message: String? = null,
    val deviceId: String? = null,
)

interface AcsCpeClient {
    fun provision(request: AcsCpeProvisionRequest): AcsCpeProvisionResponse
    fun reboot(sn: String): CpeCommandAck
    fun wifiRefresh(sn: String): CpeCommandAck
    fun telemetry(sn: String): CpeTelemetryDto?
    fun status(sn: String): AcsCpeProvisionResponse?
    fun listProfiles(): ResponseEntity<String>
    fun previewProfile(body: String): ResponseEntity<String>
    fun importProfile(body: String): ResponseEntity<String>
    fun deleteProfile(productClass: String): ResponseEntity<String>
}

data class CpeCommandAck(
    val accepted: Boolean,
    val status: CpeProvisionStatus,
    val message: String? = null,
)

class NoOpAcsCpeClient : AcsCpeClient {
    override fun provision(request: AcsCpeProvisionRequest) = AcsCpeProvisionResponse(
        sn = request.sn,
        status = CpeProvisionStatus.NA,
        message = "ACS client disabled",
    )

    override fun reboot(sn: String) = CpeCommandAck(false, CpeProvisionStatus.NA, "ACS client disabled")

    override fun wifiRefresh(sn: String) = CpeCommandAck(false, CpeProvisionStatus.NA, "ACS client disabled")

    override fun telemetry(sn: String): CpeTelemetryDto? = null

    override fun status(sn: String): AcsCpeProvisionResponse? = null

    override fun listProfiles(): ResponseEntity<String> =
        ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("[]")

    override fun previewProfile(body: String): ResponseEntity<String> = acsDisabled()

    override fun importProfile(body: String): ResponseEntity<String> = acsDisabled()

    override fun deleteProfile(productClass: String): ResponseEntity<String> = acsDisabled()

    private fun acsDisabled(): ResponseEntity<String> =
        throw RestClientException("ACS client disabled")
}
