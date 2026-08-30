package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.oltgateway.service.*
import com.dscorp.wispadmin.oltgateway.domain.repository.OltMgrOnuRepository
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.event.TransactionPhase
import java.time.Instant

@Service
class OltHistoryService(
    private val properties: ServiceHealthProperties, private val identity: IdentityService,
    private val onus: OltMgrOnuRepository, private val optical: OpticalSampleRepository,
    private val states: OnuStateEventRepository, private val cursors: HealthCursorRepository,
    private val runs: TelemetryRunRepository
) {
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT, fallbackExecution=true)
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    fun failure(event: OltOpticalFailure) {
        if (!properties.enabled || !properties.opticalEnabled) return
        runs.save(TelemetryRun(source="OLT_OPTICAL",equipmentKey=event.oltId.toString(),startedAt=event.observedAt,
            completedAt=event.observedAt,qualityStatus=Quality.ERROR,errorCount=1,errorReason=event.reason))
    }

    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT, fallbackExecution=true)
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    fun optical(event: OltOpticalObservation) {
        if (!properties.enabled || !properties.opticalEnabled) return
        val now = Instant.now()
        val run = runs.save(TelemetryRun(source="OLT_OPTICAL", equipmentKey=event.oltId.toString(), startedAt=event.observedAt))
        val inventory = onus.findByOlt_IdWithStatus(event.oltId).filter { it.deletedAt == null }
            .associateBy { Triple(it.board,it.port,it.onuIndex) }
        event.rows.forEach { row ->
            run.readCount++
            val onu = inventory[Triple(row.slot,row.port,row.optical.ontId)] ?: run { run.unmappedCount++; return@forEach }
            val subId = identity.resolveOnuForCollection(onu.sn)
            if (subId == null) { run.unmappedCount++; return@forEach }
            if (!properties.collects(subId)) return@forEach
            val v = row.optical
            val hasValue = listOf(v.rxPowerDbm,v.txPowerDbm,v.oltRxPowerDbm,v.temperatureC,v.biasCurrentMa,v.distanceM).any { it != null }
            optical.save(OpticalSample(subscriptionId=subId, onuId=onu.id!!, onuSn=onu.sn, oltId=event.oltId,
                board=onu.board,port=onu.port,onuIndex=onu.onuIndex,observedAt=event.observedAt,collectedAt=now,
                onuRxDbm=v.rxPowerDbm,onuTxDbm=v.txPowerDbm,oltRxDbm=v.oltRxPowerDbm,temperatureC=v.temperatureC,
                distanceM=v.distanceM,biasMa=v.biasCurrentMa,sourceRunId=run.id,
                qualityStatus=if(hasValue) Quality.FRESH else Quality.MISSING))
            run.writtenCount++
        }
        run.completedAt=now
        runs.save(run)
    }

    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT, fallbackExecution=true)
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    fun state(event: OltStateObservation) {
        if (!properties.enabled || !properties.opticalEnabled || event.state == null) return
        val subId = identity.resolveOnuForCollection(event.sn)
        if (!properties.collects(subId)) return
        val onu = onus.findBySnIgnoreCaseAndDeletedAtIsNull(event.sn).orElse(null) ?: return
        val previous = states.findTopByOnuIdOrderByObservedAtDesc(onu.id!!)
        if (previous == null || previous.state != event.state || previous.subscriptionId != subId || previous.oltId!=onu.olt.id || previous.board!=onu.board || previous.port!=onu.port) {
            states.save(OnuStateEvent(subscriptionId=subId,onuId=onu.id!!,onuSn=onu.sn,oltId=onu.olt.id!!,
                board=onu.board,port=onu.port,previousState=previous?.state,state=event.state,cause=event.cause,
                observedAt=event.observedAt))
        }
        val key="state:${onu.id}"
        val cursor=cursors.findById(key).orElse(HealthCursor(cursorKey=key))
        cursor.observedAt=event.observedAt; cursor.updatedAt=Instant.now()
        cursors.save(cursor)
    }
}
