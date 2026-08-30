package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.repository.*
import com.dscorp.wispadmin.netdiag.domain.repository.*
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.data.domain.PageRequest
import java.time.Instant

/** Reuses persisted, parsed OLT alarms. Never starts another trap/syslog listener. */
@Service
class OltAlarmHistoryService(private val properties: ServiceHealthProperties,private val logs: NetDiagOltLogEventRepository,
    private val targets: NetDiagTargetRepository,private val olts: com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOltRepository,private val onus: OltMgrOnuRepository,private val identity: IdentityService,
    private val links: IdentityLinkRepository,private val states: OnuStateEventRepository,private val cursors: HealthCursorRepository,
    private val json: ObjectMapper,private val tx: TransactionTemplate) {
    @Scheduled(fixedDelayString="\${service.health.olt-event-interval-ms:60000}",initialDelayString="\${service.health.olt-event-initial-delay-ms:60000}")
    fun copyAlarms() {
        if(!properties.enabled || !properties.opticalEnabled) return
        tx.executeWithoutResult {
            val cursor=cursors.lock("olt-events") ?: return@executeWithoutResult
            val batch=logs.findChanges(cursor.observedAt ?: Instant.now(),cursor.referenceId,PageRequest.of(0,500))
            for(event in batch) {
                if(event.reasonCode=="ONT_OFFLINE" && !event.isUnparsed && event.board!=null && event.port!=null && event.onuIndex!=null && event.targetId!=null && !states.existsBySourceAndSourceEventId("NETDIAG_OLT_ALARM",event.id!!)) {
                    val target=targets.findById(event.targetId!!).orElse(null)
                    val config=try { json.readTree(target?.monitorConfig ?: "{}") } catch (_: Exception) { json.createObjectNode() }
                    val oltName=config.path("oltId").asText()
                    val oltId=olts.findByName(oltName).orElse(null)?.id
                    val onu=oltId?.let { onus.findByOlt_IdAndBoardAndPortAndOnuIndexAndDeletedAtIsNull(it,event.board!!,event.port!!,event.onuIndex!!).orElse(null) }
                    val id=onu?.sn?.let(identity::resolveOnu)
                    if(onu!=null && id!=null && properties.collects(id)) {
                        val link=links.findBySubscriptionIdAndValidToIsNull(id).firstOrNull { it.kind=="PON" && it.identityValue=="${onu.olt.id}:${onu.board}:${onu.port}" }
                        // Never retroactively attach a pre-migration alarm through today's inventory.
                        if(link!=null && !event.receivedAt.isBefore(link.validFrom)) {
                            val previous=states.findTopByOnuIdOrderByObservedAtDesc(onu.id!!)
                            states.save(OnuStateEvent(sourceEventId=event.id,subscriptionId=id,onuId=onu.id!!,onuSn=onu.sn,oltId=onu.olt.id!!,
                                board=onu.board,port=onu.port,previousState=previous?.takeIf { !it.observedAt.isAfter(event.receivedAt) }?.state,state=if(event.isClear) "online" else "offline",
                                cause="ONT_OFFLINE",observedAt=event.receivedAt,source="NETDIAG_OLT_ALARM"))
                            val key="state:${onu.id}"
                            val seen=cursors.findById(key).orElse(HealthCursor(cursorKey=key))
                            if(seen.observedAt==null || event.receivedAt>seen.observedAt) { seen.observedAt=event.receivedAt; cursors.save(seen) }
                        }
                    }
                }
                cursor.observedAt=event.receivedAt; cursor.referenceId=event.id!!; cursor.updatedAt=Instant.now()
            }
            cursors.save(cursor)
        }
    }
}
