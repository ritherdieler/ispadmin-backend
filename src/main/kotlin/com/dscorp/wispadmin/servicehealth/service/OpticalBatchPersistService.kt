package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.events.LiveOnuState
import com.dscorp.wispadmin.events.LiveTelemetryPort
import com.dscorp.wispadmin.events.OnuOpticalBatchItem
import com.dscorp.wispadmin.events.OnuOpticalBatchPayload
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthScope
import com.dscorp.wispadmin.servicehealth.domain.OpticalSample
import com.dscorp.wispadmin.servicehealth.domain.Quality
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.repository.OpticalSampleRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class OpticalBatchPersistService(
    private val properties: ServiceHealthProperties,
    private val scope: ServiceHealthScope,
    private val identity: IdentityService,
    private val onuPort: ObjectProvider<HealthOnuPort>,
    private val optical: OpticalSampleRepository,
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
        for (item in payload.onus) {
            val subscriptionId = persistItem(inventory, item, payload.oltId, now) ?: continue
            touched += subscriptionId
        }
        return touched.toList()
    }

    private fun persistItem(
        inventory: HealthOnuPort,
        item: OnuOpticalBatchItem,
        oltId: Long,
        now: Instant,
    ): Int? {
        if (item.onuExternalId.isBlank()) {
            logger.info("Skipping optical batch item without onuExternalId sn={}", item.sn)
            return null
        }
        val onu = inventory.findByExternalId(item.onuExternalId)
            ?: inventory.findBySn(item.sn)
            ?: run {
                logger.info("Discarding optical batch item unknown onuExternalId={}", item.onuExternalId)
                return null
            }
        val subscriptionId = identity.resolveOnuForCollection(item.sn) ?: return null
        if (!scope.collects(subscriptionId)) return null
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
                qualityStatus = Quality.FRESH,
            )
        )
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
