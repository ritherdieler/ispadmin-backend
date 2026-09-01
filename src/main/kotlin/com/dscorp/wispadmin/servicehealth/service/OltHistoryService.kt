package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthScope
import com.dscorp.wispadmin.servicehealth.domain.*
import com.dscorp.wispadmin.servicehealth.port.HealthOltIngestPort
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.port.HealthOpticalObservation
import com.dscorp.wispadmin.servicehealth.repository.*
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class OltHistoryService(
    private val properties: ServiceHealthProperties,
    private val scope: ServiceHealthScope,
    private val identity: IdentityService,
    private val onuPort: ObjectProvider<HealthOnuPort>,
    private val optical: OpticalSampleRepository,
    private val states: OnuStateEventRepository,
    private val cursors: HealthCursorRepository,
    private val runs: TelemetryRunRepository
) : HealthOltIngestPort {

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    override fun onOpticalFailure(oltId: Long, observedAt: Instant, reason: String) {
        if (!properties.enabled || !properties.opticalEnabled) return
        runs.save(TelemetryRun(source="OLT_OPTICAL",equipmentKey=oltId.toString(),startedAt=observedAt,
            completedAt=observedAt,qualityStatus=Quality.ERROR,errorCount=1,errorReason=reason))
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    override fun onOptical(observation: HealthOpticalObservation) {
        if (!properties.enabled || !properties.opticalEnabled) return
        val inventoryPort=onuPort.ifAvailable ?: return
        val now = Instant.now()
        val run = runs.save(TelemetryRun(source="OLT_OPTICAL", equipmentKey=observation.oltId.toString(), startedAt=observation.observedAt))
        val inventory = inventoryPort.findByOlt(observation.oltId).associateBy { Triple(it.board,it.port,it.onuIndex) }
        observation.rows.forEach { row ->
            run.readCount++
            val onu = inventory[Triple(row.slot,row.port,row.ontId)] ?: run { run.unmappedCount++; return@forEach }
            val subId = identity.resolveOnuForCollection(onu.sn)
            if (subId == null) { run.unmappedCount++; return@forEach }
            if (!scope.collects(subId)) return@forEach
            val hasValue = listOf(row.rxPowerDbm,row.txPowerDbm,row.oltRxPowerDbm,row.temperatureC,row.biasCurrentMa,row.distanceM).any { it != null }
            optical.save(OpticalSample(subscriptionId=subId, onuId=onu.id, onuSn=onu.sn, oltId=observation.oltId,
                board=onu.board,port=onu.port,onuIndex=onu.onuIndex,observedAt=observation.observedAt,collectedAt=now,
                onuRxDbm=row.rxPowerDbm,onuTxDbm=row.txPowerDbm,oltRxDbm=row.oltRxPowerDbm,temperatureC=row.temperatureC,
                distanceM=row.distanceM,biasMa=row.biasCurrentMa,sourceRunId=run.id,
                qualityStatus=if(hasValue) Quality.FRESH else Quality.MISSING))
            run.writtenCount++
        }
        run.completedAt=now
        runs.save(run)
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    override fun onState(sn: String, state: String?, cause: String?, observedAt: Instant) {
        if (!properties.enabled || !properties.opticalEnabled || state == null) return
        val onu = onuPort.ifAvailable?.findBySn(sn) ?: return
        val subId = identity.resolveOnuForCollection(sn)
        if (!scope.collects(subId)) return
        val previous = states.findTopByOnuIdOrderByObservedAtDesc(onu.id)
        val oltId = onu.oltId ?: return
        if (previous == null || previous.state != state || previous.subscriptionId != subId || previous.oltId!=oltId || previous.board!=onu.board || previous.port!=onu.port) {
            states.save(OnuStateEvent(subscriptionId=subId,onuId=onu.id,onuSn=onu.sn,oltId=oltId,
                board=onu.board,port=onu.port,previousState=previous?.state,state=state,cause=cause,
                observedAt=observedAt))
        }
        val key="state:${onu.id}"
        val cursor=cursors.findById(key).orElse(HealthCursor(cursorKey=key))
        cursor.observedAt=observedAt; cursor.updatedAt=Instant.now()
        cursors.save(cursor)
    }
}
