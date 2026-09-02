package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthScope
import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.repository.*
import com.dscorp.wispadmin.servicehealth.port.HealthTrafficPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionDirectoryPort
import org.springframework.beans.factory.ObjectProvider
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class HealthEvaluationService(
    private val properties: ServiceHealthProperties, private val scope: ServiceHealthScope, private val subscriptions: SubscriptionDirectoryPort,
    private val identity: IdentityService, private val reader: HealthEvidenceReader, private val engine: DiagnosisEngine,
    private val events: HealthEventRepository, private val current: HealthCurrentRepository,
    private val evidence: EvidenceLinkRepository, private val runs: TelemetryRunRepository, private val trafficPort: ObjectProvider<HealthTrafficPort>,
    private val trafficEvidence: TrafficEvidenceRepository, private val cursors: HealthCursorRepository,
    private val tx: TransactionTemplate, private val json: ObjectMapper
) {
    @Scheduled(fixedDelayString="\${service.health.evaluation-interval-ms:60000}",initialDelayString="\${service.health.evaluation-initial-delay-ms:60000}")
    fun evaluate() {
        val collectIds=scope.collectionSubscriptionIds()
        if(!properties.enabled || collectIds.isEmpty()) return
        tx.executeWithoutResult {
            val cursor=cursors.lock("traffic-consumer") ?: return@executeWithoutResult
            val now=Instant.now()
            val zone=ZoneId.of("America/Lima")
            var after=LocalDateTime.ofInstant((cursor.observedAt ?: now.minusSeconds(86400)).minusSeconds(120),zone)
            var id=0L
            while(true) {
                val changes=trafficPort.ifAvailable?.findAnomalyChanges(after,id,PageRequest.of(0,250)).orEmpty()
                for(e in changes) {
                    val subscriptionId=e.subscriptionId ?: continue
                    trafficEvidence.save(TrafficEvidence(eventId=e.id,subscriptionId=subscriptionId,routerId=e.hostDeviceId,
                        eventStatus=e.eventStatus,anomalyType=e.anomalyType,observedAt=e.lastEvaluatedAt.atZone(zone).toInstant(),
                        coveragePct=e.coveragePct,confidence=e.confidence,evidenceJson=e.evidenceJson))
                }
                val last=changes.lastOrNull() ?: break
                after=last.lastEvaluatedAt; id=last.id
                cursor.observedAt=after.atZone(zone).toInstant(); cursor.referenceId=id; cursor.updatedAt=now
                if(changes.size<250) break
            }
            cursors.save(cursor)
        }
        // Transactions are bounded by subscription, not by the entire fleet.
        for(id in collectIds) tx.executeWithoutResult {
            cursors.lock("evaluation") ?: return@executeWithoutResult
            if(!subscriptions.exists(id)) return@executeWithoutResult
            val now=Instant.now()
            identity.reconcile(id,now)
            val input=reader.read(id,now)
            val summary=engine.evaluate(input)
            for(source in input.sources.filter { (it.source=="TRAFFIC" && it.metric=="collector") || (it.source=="NETDIAG" && it.metric=="cpu_load") }) {
                val ref=source.referenceId?.toLongOrNull() ?: continue
                val equipment=input.identity["ROUTER"] ?: continue
                val previous=runs.findTopBySourceAndEquipmentKeyOrderByStartedAtDesc(source.source,equipment)
                if(previous?.domainRunId!=ref) runs.save(TelemetryRun(source=source.source,equipmentKey=equipment,domainRunId=ref,
                    startedAt=source.observedAt ?: now,completedAt=source.observedAt,qualityStatus=source.qualityStatus))
            }
            if(!properties.correlationEnabled) {
                current.save(HealthCurrent(subscriptionId=id,evaluatedAt=now,summaryJson=json.writeValueAsString(summary)))
                return@executeWithoutResult
            }
            val snapshot=json.writeValueAsString(summary.identity)
            val open=events.findBySubscriptionIdAndEventStatus(id,"OPEN")
            for(previous in open) {
                val next=summary.diagnoses.firstOrNull { it.diagnosisCode==previous.diagnosisCode }
                if(next==null || previous.identitySnapshotJson!=snapshot || previous.diagnosisJson!=json.writeValueAsString(next)) {
                    // Preserve the evidence and topology used by this historical conclusion.
                    previous.eventStatus=if(next==null) "CLOSED" else "SUPERSEDED"; previous.endedAt=now; events.save(previous)
                }
            }
            for(d in summary.diagnoses) {
                if(open.any { it.diagnosisCode==d.diagnosisCode && it.eventStatus=="OPEN" }) continue
                val event=events.save(HealthEvent(subscriptionId=id,diagnosisCode=d.diagnosisCode,confidence=d.confidence,
                    observedAt=now,evaluatedAt=now,diagnosisJson=json.writeValueAsString(d),identitySnapshotJson=snapshot))
                d.evidence.forEach { e ->
                    evidence.save(EvidenceLink(healthEventId=event.id!!,source=e.source,referenceId="${e.metric}:${e.referenceId ?: "derived"}",observedAt=e.observedAt,summaryJson=json.writeValueAsString(e)))
                }
            }
            current.save(HealthCurrent(subscriptionId=id,evaluatedAt=now,summaryJson=json.writeValueAsString(summary)))
        }
    }
}
