package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.events.LiveOnuState
import com.dscorp.wispadmin.events.LiveTelemetryPort
import com.dscorp.wispadmin.events.OnuOpticalBatchItem
import com.dscorp.wispadmin.events.OnuOpticalBatchPayload
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.OpticalSample
import com.dscorp.wispadmin.servicehealth.domain.Quality
import com.dscorp.wispadmin.servicehealth.domain.TelemetryRun
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.repository.OpticalSampleRepository
import com.dscorp.wispadmin.servicehealth.repository.TelemetryRunRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class OpticalBatchPersistService(
    private val properties: ServiceHealthProperties,
    private val identity: IdentityService,
    private val onuPort: ObjectProvider<HealthOnuPort>,
    private val optical: OpticalSampleRepository,
    private val runs: TelemetryRunRepository,
    private val liveTelemetry: ObjectProvider<LiveTelemetryPort>,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(OpticalBatchPersistService::class.java)

    @Transactional
    fun persistFromEventJson(payloadJson: String): List<Int> {
        val payload = objectMapper.readValue(payloadJson, OnuOpticalBatchPayload::class.java)
        return persist(payload)
    }

    @Transactional
    fun persist(payload: OnuOpticalBatchPayload): List<Int> {
        if (!properties.enabled || !properties.opticalEnabled) return emptyList()
        val inventory = onuPort.ifAvailable ?: return emptyList()
        val now = Instant.now()
        val touched = linkedSetOf<Int>()
        val run = TelemetryRun(
            source = "OLT_OPTICAL",
            equipmentKey = payload.oltId.toString(),
            startedAt = payload.polledAt,
        )
        for (item in payload.onus) {
            run.readCount++
            val subscriptionId = persistItem(inventory, item, payload.oltId, now, run) ?: continue
            touched += subscriptionId
        }
        run.completedAt = Instant.now()
        runs.save(run)
        return touched.toList()
    }

    private fun persistItem(
        inventory: HealthOnuPort,
        item: OnuOpticalBatchItem,
        oltId: Long,
        now: Instant,
        run: TelemetryRun,
    ): Int? {
        if (item.onuExternalId.isBlank()) {
            logger.info("Skipping optical batch item without onuExternalId sn={}", item.sn)
            run.unmappedCount++
            return null
        }
        val onu = inventory.findByExternalId(item.onuExternalId)
            ?: inventory.findBySn(item.sn)
            ?: run {
                logger.info("Discarding optical batch item unknown onuExternalId={}", item.onuExternalId)
                run.unmappedCount++
                return null
            }
        val subscriptionId = identity.resolveOnuForCollection(item.sn) ?: run {
            run.unmappedCount++
            return null
        }
        if (optical.findByOnuIdAndObservedAt(onu.id, item.polledAt) != null) {
            return subscriptionId
        }
        optical.save(
            OpticalSample(
                subscriptionId = subscriptionId,
                onuId = onu.id,
                onuSn = onu.sn,
                oltId = onu.oltId ?: oltId,
                board = onu.board,
                port = onu.port,
                onuIndex = onu.onuIndex,
                observedAt = item.polledAt,
                collectedAt = now,
                onuRxDbm = item.onuRxDbm,
                onuTxDbm = item.onuTxDbm,
                oltRxDbm = item.oltRxDbm,
                temperatureC = item.temperatureC,
                distanceM = item.distanceM,
                biasMa = item.biasCurrentMa,
                sourceRunId = null,
                qualityStatus = Quality.FRESH,
            )
        )
        run.writtenCount++
        liveTelemetry.ifUnique?.putOnu(
            subscriptionId,
            LiveOnuState(
                sn = item.sn,
                runState = item.runState,
                rxPowerDbm = item.onuRxDbm,
                observedAt = item.polledAt,
                updateKind = "optical",
            ),
        )
        return subscriptionId
    }
}
