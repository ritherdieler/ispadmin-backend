package com.dscorp.wispadmin.oltgateway.service

import com.dscorp.wispadmin.events.EventBusPort
import com.dscorp.wispadmin.events.PlatformEvent
import com.dscorp.wispadmin.events.PlatformEventTypes
import com.dscorp.wispadmin.oltgateway.api.AuthorizeOnuFormDto
import com.dscorp.wispadmin.oltgateway.client.AcsCpeClient
import com.dscorp.wispadmin.oltgateway.client.AcsCpeProvisionRequest
import com.dscorp.wispadmin.oltgateway.client.AcsCpeProvisionResponse
import com.dscorp.wispadmin.oltgateway.client.AcsCpeWifiRequest
import com.dscorp.wispadmin.oltgateway.dto.CpeCommandResponseDto
import com.dscorp.wispadmin.oltgateway.dto.CpeProvisionStatus
import com.dscorp.wispadmin.oltgateway.dto.CpeTelemetryDto
import com.dscorp.wispadmin.oltgateway.dto.OltActivationStatus
import com.dscorp.wispadmin.oltgateway.dto.OnuActivateRequestDto
import com.dscorp.wispadmin.oltgateway.dto.OnuActivateResponseDto
import com.dscorp.wispadmin.oltgateway.dto.OnuActivationStatusDto
import com.dscorp.wispadmin.oltgateway.exception.OltGatewayConflictException
import com.dscorp.wispadmin.transport.RegistrationTiming
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class OnuActivationService(
    private val oltManagerFacade: OltManagerFacade,
    private val acsCpeClient: AcsCpeClient,
    private val eventBus: EventBusPort,
    private val journal: ActivationJournal = MemoryActivationJournal(),
    private val acsExecutor: Executor = Executors.newFixedThreadPool(4),
    private val timing: RegistrationTiming = RegistrationTiming.NOOP,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val autofindTimeoutMs: Long = DEFAULT_AUTOFIND_TIMEOUT_MS,
    private val autofindPollMs: Long = DEFAULT_AUTOFIND_POLL_MS,
) {
    private val log = LoggerFactory.getLogger(OnuActivationService::class.java)
    fun activate(rawRequest: OnuActivateRequestDto): OnuActivateResponseDto {
        val request=rawRequest.copy(sn=rawRequest.sn.trim().uppercase())
        require(request.sn.isNotBlank()) { "ONU serial is required" }
        val (operation,acquired)=journal.acquire(request)
        trace("activate", operation, "acquired=$acquired")
        if(!acquired) return response(operation.status)
        if(operation.stage=="OLT") {
            val authorized=timing.span("gateway.olt.authorize", mapOf("sn" to request.sn)) { authorizeOlt(request) }
            val externalId=authorized?.first
            if(externalId==null) {
                operation.status=OnuActivationStatusDto(sn=request.sn,oltStatus=OltActivationStatus.FAILED,cpeStatus=CpeProvisionStatus.NA,message=authorized?.second ?: "OLT authorize failed",updatedAtEpochMs=Instant.now().toEpochMilli())
                operation.stage="DONE"
                journal.save(operation)
                return response(operation.status)
            }
            operation.status=OnuActivationStatusDto(externalId,request.sn,OltActivationStatus.COMPLETE,CpeProvisionStatus.PENDING,updatedAtEpochMs=Instant.now().toEpochMilli())
            operation.stage="ACS"
            journal.save(operation)
        }
        if(operation.stage=="ACS_STATUS") {
            val remote=runCatching { acsCpeClient.status(request.sn) }.getOrNull()
            if(remote!=null && remote.status!=CpeProvisionStatus.NA) {
                if(remote.status==CpeProvisionStatus.PENDING) {
                    operation.status=operation.status.copy(
                        deviceId=remote.deviceId ?: operation.status.deviceId,
                        message=remote.message ?: operation.status.message,
                        updatedAtEpochMs=Instant.now().toEpochMilli(),
                    )
                    operation.leaseUntil=System.currentTimeMillis()+30_000
                    journal.save(operation)
                    trace("acs-status-pending", operation, "remoteDevice=${remote.deviceId}")
                    return response(operation.status)
                }
                operation.status=operation.status.copy(cpeStatus=remote.status,message=remote.message,deviceId=remote.deviceId ?: operation.status.deviceId,updatedAtEpochMs=Instant.now().toEpochMilli())
                operation.stage="DONE"
                journal.save(operation)
                publishPending()
                return response(operation.status)
            }
            if(operation.attempts>=MAX_PROVISION_ATTEMPTS) {
                operation.leaseUntil=System.currentTimeMillis()+30_000
                journal.save(operation)
                trace("acs-attempts-exhausted", operation)
                return response(operation.status)
            }
            operation.stage="ACS"
            journal.save(operation)
        }
        val pending=response(operation.status)
        acsExecutor.execute { runAcs(operation) }
        return pending
    }

    fun statusBySn(sn: String): OnuActivationStatusDto? {
        val normalized = sn.trim().uppercase()
        val operation = journal.bySn(normalized) ?: return null
        val stored = operation.status
        if (!stored.deviceId.isNullOrBlank()) {
            log.info(
                "FIBER_TRACE gateway event=status-stored sn={} olt={} cpe={} deviceId={} message={}",
                normalized,
                stored.oltStatus,
                stored.cpeStatus,
                stored.deviceId,
                stored.message?.take(180),
            )
            return stored
        }
        val remote = runCatching { acsCpeClient.status(normalized) }.getOrNull() ?: return stored
        val deviceId = remote.deviceId?.takeIf { it.isNotBlank() } ?: return stored
        val updated = stored.copy(deviceId = deviceId, updatedAtEpochMs = Instant.now().toEpochMilli())
        operation.status = updated
        journal.save(operation)
        return updated
    }
    fun statusByExternalId(externalId: String): OnuActivationStatusDto? = journal.byExternalId(externalId)?.status

    fun clearJournal(sn: String) {
        journal.clear(sn.trim().uppercase())
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="\${olt.gateway.activation-recovery-ms:30000}")
    fun recover() {
        journal.pending().forEach { operation ->
            runCatching { activate(operation.request) }.onFailure { log.warn("Activation recovery failed id={}",operation.operationId) }
        }
        publishPending()
    }

    private fun trace(event: String, operation: ActivationOperation, extra: String = "") {
        val status = operation.status
        log.info(
            "FIBER_TRACE gateway event={} sn={} stage={} attempts={} olt={} cpe={} deviceId={} message={} {}",
            event,
            status.sn,
            operation.stage,
            operation.attempts,
            status.oltStatus,
            status.cpeStatus,
            status.deviceId,
            status.message?.take(180),
            extra,
        )
    }

    private fun response(status: OnuActivationStatusDto)=OnuActivateResponseDto(status.uniqueExternalId,status.sn,status.oltStatus,status.cpeStatus,status.message,status.deviceId)

    fun reboot(sn: String): CpeCommandResponseDto {
        val ack = acsCpeClient.reboot(sn)
        return CpeCommandResponseDto(ack.accepted, ack.status, ack.message)
    }

    fun provisionCpe(sn: String, request: AcsCpeProvisionRequest): AcsCpeProvisionResponse {
        val normalized = sn.trim().uppercase()
        log.info(
            "FIBER_TRACE gateway event=provision-cpe sn={} vlan={} pppoeUser={} ssid24={}",
            normalized,
            request.wanVlanId,
            request.pppoeUsername,
            request.wifiSsid24,
        )
        val outcome = acsCpeClient.provision(request.copy(sn = normalized))
        log.info(
            "FIBER_TRACE gateway event=provision-cpe-result sn={} status={} deviceId={} message={}",
            normalized,
            outcome.status,
            outcome.deviceId,
            outcome.message?.take(180),
        )
        return outcome
    }

    fun setWifi(sn: String, request: AcsCpeWifiRequest): CpeCommandResponseDto {
        val ack = acsCpeClient.setWifi(sn.trim().uppercase(), request)
        return CpeCommandResponseDto(ack.accepted, ack.status, ack.message)
    }

    fun accessLayout(sn: String): com.dscorp.wispadmin.oltgateway.dto.CpeAccessLayoutDto? =
        acsCpeClient.accessLayout(sn.trim().uppercase())

    fun wifiRefresh(sn: String): CpeCommandResponseDto {
        val ack = acsCpeClient.wifiRefresh(sn)
        return CpeCommandResponseDto(ack.accepted, ack.status, ack.message)
    }

    fun telemetry(sn: String): CpeTelemetryDto? = acsCpeClient.telemetry(sn)

    private fun authorizeOlt(request: OnuActivateRequestDto): Pair<String?, String?>? {
        return try {
            val existing = oltManagerFacade.externalIdBySn(request.sn)
            if (!existing.isNullOrBlank()) {
                oltManagerFacade.deleteOnu(existing)
                if (!waitUntilUnconfigured(request.sn)) {
                    return null to "ONU deleted but not back in autofind"
                }
            }
            authorizeOnce(request)
        } catch (ex: OltGatewayConflictException) {
            val existing = oltManagerFacade.externalIdBySn(request.sn)
            if (existing.isNullOrBlank()) return null to (ex.message ?: "already authorized")
            try {
                oltManagerFacade.deleteOnu(existing)
                if (!waitUntilUnconfigured(request.sn)) {
                    return null to "ONU deleted but not back in autofind"
                }
                authorizeOnce(request)
            } catch (deleteEx: Exception) {
                log.warn("OLT delete failed for SN={}: {}", request.sn, deleteEx.message)
                null to (deleteEx.message ?: "ONU delete failed")
            }
        } catch (ex: Exception) {
            log.warn("OLT authorize failed for SN={}: {}", request.sn, ex.message)
            null to (ex.message ?: "OLT authorize failed")
        }
    }

    private fun authorizeOnce(request: OnuActivateRequestDto): Pair<String?, String?> {
        val response = oltManagerFacade.authorizeOnu(request.toAuthorizeForm())
        val id = response.unique_external_id?.takeIf { it.isNotBlank() }
        return if (id == null) null to (response.message ?: "missing unique_external_id")
        else id to null
    }

    private fun waitUntilUnconfigured(sn: String): Boolean {
        val deadline = System.currentTimeMillis() + autofindTimeoutMs
        while (true) {
            val items = runCatching { oltManagerFacade.unconfiguredOnus().response }.getOrDefault(emptyList())
            if (items.any { OnuSerialMatcher.matches(sn, it.sn) }) return true
            if (System.currentTimeMillis() >= deadline) return false
            sleeper(autofindPollMs)
        }
    }

    private fun runAcs(operation: ActivationOperation) {
        val request=operation.request
        val externalId=operation.status.uniqueExternalId ?: return
        operation.attempts+=1
        journal.save(operation)
        trace("acs-start", operation)
        try {
            val outcome=timing.span("gateway.acs.provision", mapOf("sn" to request.sn)) {
                acsCpeClient.provision(AcsCpeProvisionRequest(
                sn=request.sn,uniqueExternalId=externalId,onuType=request.onuType,ip=request.ip,ipSegment=request.ipSegment,
                wanVlanId=request.vlan.toIntOrNull() ?: 1,wifiSsid24=request.wifiSsid24,wifiPassword24=request.wifiPassword24,
                wifiSsid5=request.wifiSsid5,wifiPassword5=request.wifiPassword5,
                pppoeUsername=request.pppoeUsername,pppoePassword=request.pppoePassword))
            }
            operation.status=operation.status.copy(cpeStatus=outcome.status,message=outcome.message,deviceId=outcome.deviceId ?: operation.status.deviceId,updatedAtEpochMs=Instant.now().toEpochMilli())
            operation.stage=if(outcome.status==CpeProvisionStatus.PENDING) "ACS_STATUS" else "DONE"
            trace("acs-result", operation)
            operation.leaseUntil=System.currentTimeMillis()+30_000
            journal.save(operation)
            publishPending()
        } catch(ex: Exception) {
            operation.stage="ACS_STATUS"
            operation.leaseUntil=System.currentTimeMillis()+30_000
            journal.save(operation)
            log.warn(
                "FIBER_TRACE gateway event=acs-unconfirmed sn={} operation={} type={} error={}",
                request.sn,
                operation.operationId,
                ex.javaClass.simpleName,
                ex.message?.take(180),
            )
        }
    }

    private fun publishPending() {
        journal.unpublished().forEach { operation ->
            val event=PlatformEvent(type=PlatformEventTypes.CPE_PROVISIONING,sn=operation.request.sn,
                occurredAt=Instant.ofEpochMilli(operation.status.updatedAtEpochMs),eventId=operation.operationId,
                operationId=operation.operationId,producer="oltgateway",
                payloadJson="""{"cpeStatus":"${operation.status.cpeStatus}"}""")
            if(eventBus.tryPublish(event)) journal.published(operation.operationId)
        }
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

    companion object {
        const val MAX_PROVISION_ATTEMPTS = 3
        const val DEFAULT_AUTOFIND_TIMEOUT_MS = 90_000L
        const val DEFAULT_AUTOFIND_POLL_MS = 5_000L
    }
}
