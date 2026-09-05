package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.events.EventBusPort
import com.dscorp.wispadmin.events.PlatformEvent
import com.dscorp.wispadmin.events.PlatformEventTypes
import com.dscorp.wispadmin.oltgateway.api.AuthorizeOnuFormDto
import com.dscorp.wispadmin.oltgateway.client.AcsCpeClient
import com.dscorp.wispadmin.oltgateway.client.AcsCpeProvisionRequest
import com.dscorp.wispadmin.oltgateway.dto.CpeCommandResponseDto
import com.dscorp.wispadmin.oltgateway.dto.CpeProvisionStatus
import com.dscorp.wispadmin.oltgateway.dto.CpeTelemetryDto
import com.dscorp.wispadmin.oltgateway.dto.OltActivationStatus
import com.dscorp.wispadmin.oltgateway.dto.OnuActivateRequestDto
import com.dscorp.wispadmin.oltgateway.dto.OnuActivateResponseDto
import com.dscorp.wispadmin.oltgateway.dto.OnuActivationStatusDto
import com.dscorp.wispadmin.oltgateway.exception.OltGatewayConflictException
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class OnuActivationService(
    private val oltManagerFacade: OltManagerFacade,
    private val acsCpeClient: AcsCpeClient,
    private val eventBus: EventBusPort,
    private val acsExecutor: Executor = Executors.newCachedThreadPool(),
) {
    private val log = LoggerFactory.getLogger(OnuActivationService::class.java)
    private val bySn = ConcurrentHashMap<String, OnuActivationStatusDto>()
    private val byExternalId = ConcurrentHashMap<String, String>()

    fun activate(request: OnuActivateRequestDto): OnuActivateResponseDto {
        val sn = request.sn.trim()
        val authorized = authorizeOlt(request)
        if (authorized == null) {
            val failed = OnuActivateResponseDto(
                sn = sn,
                oltStatus = OltActivationStatus.FAILED,
                cpeStatus = CpeProvisionStatus.NA,
                message = "OLT authorize failed",
            )
            remember(failed)
            return failed
        }
        val (externalId, oltError) = authorized
        if (externalId == null) {
            val failed = OnuActivateResponseDto(
                sn = sn,
                oltStatus = OltActivationStatus.FAILED,
                cpeStatus = CpeProvisionStatus.NA,
                message = oltError,
            )
            remember(failed)
            return failed
        }
        val pending = OnuActivateResponseDto(
            uniqueExternalId = externalId,
            sn = sn,
            oltStatus = OltActivationStatus.COMPLETE,
            cpeStatus = CpeProvisionStatus.PENDING,
        )
        remember(pending)
        acsExecutor.execute { runAcs(request, externalId) }
        return pending
    }

    fun statusBySn(sn: String): OnuActivationStatusDto? = bySn[sn.trim().uppercase()]

    fun statusByExternalId(externalId: String): OnuActivationStatusDto? {
        val sn = byExternalId[externalId] ?: return acsCpeClient.status(externalId)?.let {
            OnuActivationStatusDto(
                uniqueExternalId = externalId,
                sn = it.sn,
                oltStatus = OltActivationStatus.COMPLETE,
                cpeStatus = it.status,
                message = it.message,
                updatedAtEpochMs = Instant.now().toEpochMilli(),
            )
        }
        return statusBySn(sn)
    }

    fun reboot(sn: String): CpeCommandResponseDto {
        val ack = acsCpeClient.reboot(sn)
        return CpeCommandResponseDto(ack.accepted, ack.status, ack.message)
    }

    fun wifiRefresh(sn: String): CpeCommandResponseDto {
        val ack = acsCpeClient.wifiRefresh(sn)
        return CpeCommandResponseDto(ack.accepted, ack.status, ack.message)
    }

    fun telemetry(sn: String): CpeTelemetryDto? = acsCpeClient.telemetry(sn)

    private fun authorizeOlt(request: OnuActivateRequestDto): Pair<String?, String?>? {
        return try {
            val response = oltManagerFacade.authorizeOnu(request.toAuthorizeForm())
            val id = response.unique_external_id?.takeIf { it.isNotBlank() }
            if (id == null) null to (response.message ?: "missing unique_external_id")
            else id to null
        } catch (ex: OltGatewayConflictException) {
            val existing = oltManagerFacade.externalIdBySn(request.sn)
            if (existing.isNullOrBlank()) null to (ex.message ?: "already authorized")
            else existing to null
        } catch (ex: Exception) {
            log.warn("OLT authorize failed for SN={}: {}", request.sn, ex.message)
            null to (ex.message ?: "OLT authorize failed")
        }
    }

    private fun runAcs(request: OnuActivateRequestDto, externalId: String) {
        try {
            val outcome = acsCpeClient.provision(
                AcsCpeProvisionRequest(
                    sn = request.sn,
                    uniqueExternalId = externalId,
                    onuType = request.onuType,
                    ip = request.ip,
                    ipSegment = request.ipSegment,
                    wanVlanId = request.vlan.toIntOrNull() ?: 1,
                    wifiSsid24 = request.wifiSsid24,
                    wifiPassword24 = request.wifiPassword24,
                    wifiSsid5 = request.wifiSsid5,
                    wifiPassword5 = request.wifiPassword5,
                )
            )
            remember(
                OnuActivateResponseDto(
                    uniqueExternalId = externalId,
                    sn = request.sn,
                    oltStatus = OltActivationStatus.COMPLETE,
                    cpeStatus = outcome.status,
                    message = outcome.message,
                )
            )
            eventBus.publish(
                PlatformEvent(
                    type = PlatformEventTypes.CPE_PROVISIONING,
                    sn = request.sn,
                    occurredAt = Instant.now(),
                    payloadJson = """{"cpeStatus":"${outcome.status}","uniqueExternalId":"$externalId"}""",
                )
            )
        } catch (ex: Exception) {
            log.warn("ACS provision failed for SN={}: {}", request.sn, ex.message)
            remember(
                OnuActivateResponseDto(
                    uniqueExternalId = externalId,
                    sn = request.sn,
                    oltStatus = OltActivationStatus.COMPLETE,
                    cpeStatus = CpeProvisionStatus.FAILED,
                    message = ex.message,
                )
            )
            eventBus.publish(
                PlatformEvent(
                    type = PlatformEventTypes.CPE_PROVISIONING,
                    sn = request.sn,
                    occurredAt = Instant.now(),
                    payloadJson = """{"cpeStatus":"FAILED","uniqueExternalId":"$externalId"}""",
                )
            )
        }
    }

    private fun remember(response: OnuActivateResponseDto) {
        val status = OnuActivationStatusDto(
            uniqueExternalId = response.uniqueExternalId,
            sn = response.sn,
            oltStatus = response.oltStatus,
            cpeStatus = response.cpeStatus,
            message = response.message,
            updatedAtEpochMs = Instant.now().toEpochMilli(),
        )
        bySn[response.sn.uppercase()] = status
        response.uniqueExternalId?.let { byExternalId[it] = response.sn }
    }

    private fun OnuActivateRequestDto.toAuthorizeForm() = AuthorizeOnuFormDto(
        olt_id = oltId,
        pon_type = ponType,
        board = board,
        port = port,
        sn = sn,
        vlan = vlan,
        onu_type = onuType,
        zone = zone,
        name = name,
        onu_mode = onuMode,
        custom_profile = customProfile,
    )
}
