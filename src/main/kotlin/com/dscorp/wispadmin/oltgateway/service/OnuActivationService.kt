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
    private val journal: ActivationJournal = MemoryActivationJournal(),
    private val acsExecutor: Executor = Executors.newFixedThreadPool(4),
) {
    private val log = LoggerFactory.getLogger(OnuActivationService::class.java)
    fun activate(rawRequest: OnuActivateRequestDto): OnuActivateResponseDto {
        val request=rawRequest.copy(sn=rawRequest.sn.trim().uppercase())
        require(request.sn.isNotBlank()) { "ONU serial is required" }
        val (operation,acquired)=journal.acquire(request)
        if(!acquired) return response(operation.status)
        if(operation.stage=="OLT") {
            val authorized=authorizeOlt(request)
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
                    operation.leaseUntil=System.currentTimeMillis()+30_000
                    journal.save(operation)
                    return response(operation.status)
                }
                operation.status=operation.status.copy(cpeStatus=remote.status,message=remote.message,updatedAtEpochMs=Instant.now().toEpochMilli())
                operation.stage="DONE"
                journal.save(operation)
                publishPending()
                return response(operation.status)
            }
            if(operation.attempts>=MAX_PROVISION_ATTEMPTS) {
                operation.leaseUntil=System.currentTimeMillis()+30_000
                journal.save(operation)
                return response(operation.status)
            }
            operation.stage="ACS"
            journal.save(operation)
        }
        val pending=response(operation.status)
        acsExecutor.execute { runAcs(operation) }
        return pending
    }

    fun statusBySn(sn: String): OnuActivationStatusDto? = journal.bySn(sn.trim().uppercase())?.status
    fun statusByExternalId(externalId: String): OnuActivationStatusDto? = journal.byExternalId(externalId)?.status

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="\${olt.gateway.activation-recovery-ms:30000}")
    fun recover() {
        journal.pending().forEach { operation ->
            runCatching { activate(operation.request) }.onFailure { log.warn("Activation recovery failed id={}",operation.operationId) }
        }
        publishPending()
    }

    private fun response(status: OnuActivationStatusDto)=OnuActivateResponseDto(status.uniqueExternalId,status.sn,status.oltStatus,status.cpeStatus,status.message)

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

    private fun runAcs(operation: ActivationOperation) {
        val request=operation.request
        val externalId=operation.status.uniqueExternalId ?: return
        operation.attempts+=1
        journal.save(operation)
        try {
            val outcome=acsCpeClient.provision(AcsCpeProvisionRequest(
                sn=request.sn,uniqueExternalId=externalId,onuType=request.onuType,ip=request.ip,ipSegment=request.ipSegment,
                wanVlanId=request.vlan.toIntOrNull() ?: 1,wifiSsid24=request.wifiSsid24,wifiPassword24=request.wifiPassword24,
                wifiSsid5=request.wifiSsid5,wifiPassword5=request.wifiPassword5))
            operation.status=operation.status.copy(cpeStatus=outcome.status,message=outcome.message,updatedAtEpochMs=Instant.now().toEpochMilli())
            operation.stage=if(outcome.status==CpeProvisionStatus.PENDING) "ACS_STATUS" else "DONE"
            operation.leaseUntil=System.currentTimeMillis()+30_000
            journal.save(operation)
            publishPending()
        } catch(ex: Exception) {
            operation.stage="ACS_STATUS"
            operation.leaseUntil=System.currentTimeMillis()+30_000
            journal.save(operation)
            log.warn("ACS outcome unconfirmed operation={}",operation.operationId)
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
    }
}
