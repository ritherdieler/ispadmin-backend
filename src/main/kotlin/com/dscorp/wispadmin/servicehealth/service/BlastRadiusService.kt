package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.port.HealthNetDiagPort
import com.dscorp.wispadmin.servicehealth.repository.*
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Service
class BlastRadiusService(
    private val properties: ServiceHealthProperties,
    private val reader: HealthEvidenceReader,
    private val events: HealthEventRepository,
    private val netDiagPort: ObjectProvider<HealthNetDiagPort>,
    private val affected: IncidentSubscriptionRepository,
    private val cursors: HealthCursorRepository,
    private val tx: TransactionTemplate,
    private val json: ObjectMapper
) {
    @Scheduled(fixedDelayString="\${service.health.blast-radius-interval-ms:60000}",initialDelayString="\${service.health.blast-radius-initial-delay-ms:90000}")
    fun reconcile() {
        if(!properties.enabled || !properties.correlationEnabled || !properties.sharedIncidentsEnabled || properties.pilotSubscriptionIds.isEmpty()) return
        val port=netDiagPort.ifAvailable ?: return
        tx.executeWithoutResult {
            cursors.lock("blast-radius") ?: return@executeWithoutResult
            val now=Instant.now()
            val open=events.findByEventStatus("OPEN").filter { it.diagnosisCode=="GPON_DOWN" && properties.collects(it.subscriptionId) }
            val inputs=open.associate { it.subscriptionId to reader.read(it.subscriptionId,now) }
            val stillAffected=mutableSetOf<Pair<Long,Int>>()
            val compatible=setOf("GPON_SHARED_DOWN","LINK_DOWN","UPSTREAM_PROBE_FAIL","SNMP_TRAP_LINK_DOWN","PON_PORT_DOWN","PON_PORT_HW_FAULT","PON_MASS_POWER_OFF","PON_PROTECTION_FIBER")
            for(event in open) {
                val input=inputs[event.subscriptionId] ?: continue
                val gpon=input.sources.firstOrNull { it.metric=="run_state" }
                if(gpon?.qualityStatus!=Quality.FRESH || gpon.value!="offline") continue
                val parent=input.targets.asSequence().flatMap { target ->
                    listOf("OPEN","ACKNOWLEDGED","SILENCED").flatMap { port.incidentsByTargetAndStatus(target.id ?: -1,it) }.asSequence()
                }.firstOrNull { it.reasonCode in compatible }
                var incident=parent
                if(incident==null) {
                    val pon=input.identity["PON"] ?: continue
                    val cluster=open.filter { candidate ->
                        val other=inputs[candidate.subscriptionId] ?: return@filter false
                        val state=other.sources.firstOrNull { it.metric=="run_state" }
                        other.identity["PON"]==pon && state?.qualityStatus==Quality.FRESH && state.value=="offline" &&
                            other.flaps.any { it.state=="offline" && it.previousState=="online" && it.observedAt>=now.minusSeconds(600) } &&
                            candidate.confidence!=Confidence.LOW
                    }
                    if(cluster.mapNotNull { inputs[it.subscriptionId]?.identity?.get("ONU") }.distinct().size<3) continue
                    val target=input.targets.firstOrNull { it.name.startsWith("PON-") } ?: continue
                    val closed=port.findClosedByDedup("service-health:$pon","RESOLVED")
                    if(closed?.resolvedAt!=null && cluster.none { candidate -> inputs[candidate.subscriptionId]?.flaps?.any { it.state=="offline" && it.previousState=="online" && it.observedAt>closed.resolvedAt }==true }) continue
                    incident=port.saveIncident(target,"service-health:$pon","OPEN","P1",
                        "Caída GPON compartida: ${cluster.size} suscripciones","GPON_SHARED_DOWN",now)
                    port.saveIncidentEvent(incident,"OPENED",
                        json.writeValueAsString(mapOf("source" to "servicehealth","subscription_ids" to cluster.map { it.subscriptionId })),now)
                }
                val incidentId=incident.id ?: continue
                val row=affected.findByIncidentIdAndSubscriptionId(incidentId,event.subscriptionId)
                    ?: IncidentSubscription(incidentId=incidentId,subscriptionId=event.subscriptionId,firstSeenAt=now)
                row.state="AFFECTED"; row.recoveredAt=null; row.lastSeenAt=now; row.healthEventId=event.id
                val maintenanceIds=port.findActiveMaintenance(now).filter { it.targetId==null || input.targets.any { target -> target.id==it.targetId } }.mapNotNull { it.id }
                row.scopeJson=json.writeValueAsString(mapOf("identity" to input.identity,"maintenance_ids" to maintenanceIds))
                affected.save(row); event.suppressingIncidentId=incidentId; events.save(event)
                stillAffected+=incidentId to event.subscriptionId
            }
            val activeRelations=affected.findByState("AFFECTED")
            for(row in activeRelations) {
                if(!properties.collects(row.subscriptionId) || (row.incidentId to row.subscriptionId) in stillAffected) continue
                val state=reader.read(row.subscriptionId,now).sources.firstOrNull { it.metric=="run_state" }
                if(state?.qualityStatus==Quality.FRESH && state.value=="online") {
                    row.state="RECOVERED"; row.recoveredAt=now; affected.save(row)
                }
            }
            for(incidentId in activeRelations.map { it.incidentId }.distinct()) {
                val rows=affected.findByIncidentId(incidentId,org.springframework.data.domain.PageRequest.of(0,1))
                if(rows.totalElements==0L || affected.findByState("AFFECTED").any { it.incidentId==incidentId }) continue
                val incident=port.findIncident(incidentId) ?: continue
                if(incident.reasonCode=="GPON_SHARED_DOWN" && incident.status!="RESOLVED") {
                    port.updateIncident(incident.copy(status="RESOLVED",resolvedAt=now))
                    port.saveIncidentEvent(incident,"CLEARED","All subscribers confirmed GPON online",now)
                }
            }
        }
    }
}
