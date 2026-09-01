package com.dscorp.wispadmin.oltgateway.adapter

import com.dscorp.wispadmin.oltgateway.service.OltOpticalFailure
import com.dscorp.wispadmin.oltgateway.service.OltOpticalObservation
import com.dscorp.wispadmin.oltgateway.service.OltStateObservation
import com.dscorp.wispadmin.servicehealth.port.HealthOltIngestPort
import com.dscorp.wispadmin.servicehealth.port.HealthOpticalObservation
import com.dscorp.wispadmin.servicehealth.port.HealthOpticalRow
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
class HealthOltIngestAdapter(
    private val ingestProvider: ObjectProvider<HealthOltIngestPort>
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun opticalFailure(event: OltOpticalFailure) {
        ingestProvider.ifAvailable?.onOpticalFailure(event.oltId, event.observedAt, event.reason)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun optical(event: OltOpticalObservation) {
        ingestProvider.ifAvailable?.onOptical(
            HealthOpticalObservation(
                oltId = event.oltId,
                observedAt = event.observedAt,
                rows = event.rows.map { row ->
                    HealthOpticalRow(
                        slot = row.slot,
                        port = row.port,
                        ontId = row.optical.ontId,
                        rxPowerDbm = row.optical.rxPowerDbm,
                        txPowerDbm = row.optical.txPowerDbm,
                        oltRxPowerDbm = row.optical.oltRxPowerDbm,
                        temperatureC = row.optical.temperatureC,
                        biasCurrentMa = row.optical.biasCurrentMa,
                        distanceM = row.optical.distanceM
                    )
                }
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun state(event: OltStateObservation) {
        ingestProvider.ifAvailable?.onState(event.sn, event.state, event.cause, event.observedAt)
    }
}
