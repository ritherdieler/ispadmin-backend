package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.events.LiveOnuState
import com.dscorp.wispadmin.events.LiveTelemetryPort
import com.dscorp.wispadmin.events.PlatformEvent
import com.dscorp.wispadmin.events.PlatformEventTypes
import com.dscorp.wispadmin.servicehealth.port.CpeProvisionFlagPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

@Service
class HealthSnapshotIngestService(
    private val identity: IdentityService,
    private val summaries: HealthSummaryQueryService,
    private val liveTelemetry: ObjectProvider<LiveTelemetryPort>,
    private val json: ObjectMapper,
    private val cpeFlags: ObjectProvider<CpeProvisionFlagPort>,
    private val cpeInform: ObjectProvider<CpeInformPersistService>,
    private val opticalBatch: ObjectProvider<OpticalBatchPersistService>,
) {
    private val logger = LoggerFactory.getLogger(HealthSnapshotIngestService::class.java)

    /**
     * 3000 ONUs at a 30 min Inform interval is 1.67 events/s, so the whole
     * budget for one event is 600 ms on a single consumer thread. Log the ones
     * that eat a meaningful share of it, otherwise the stream silently lags.
     */
    private fun reevaluate(subscriptionId: Int, event: PlatformEvent) {
        val startedAt = System.nanoTime()
        try {
            summaries.reevaluate(subscriptionId, event.occurredAt)
        } catch (ex: Exception) {
            logger.warn("Snapshot reevaluate failed subscription={}: {}", subscriptionId, ex.message)
            return
        }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        if (elapsedMs >= SLOW_REEVALUATE_MS) {
            logger.warn("Slow reevaluate subscription={} type={} elapsedMs={}", subscriptionId, event.type, elapsedMs)
        } else {
            logger.debug("reevaluate subscription={} type={} elapsedMs={}", subscriptionId, event.type, elapsedMs)
        }
    }

    fun apply(event: PlatformEvent) {
        if (event.type == PlatformEventTypes.CPE_INFORM) {
            putCpeInform(event)
            val subscriptionId = event.subscriptionId ?: event.sn?.let { identity.resolveOnu(it) } ?: return
            reevaluate(subscriptionId, event)
            return
        }
        if (event.type == PlatformEventTypes.ONU_OPTICAL_BATCH) {
            putOpticalBatch(event)
            return
        }
        val subscriptionId = event.subscriptionId ?: event.sn?.let { identity.resolveOnu(it) } ?: return
        when (event.type) {
            PlatformEventTypes.TRAFFIC_LATEST -> putTraffic(subscriptionId, event)
            PlatformEventTypes.ONU_OPTICAL, PlatformEventTypes.ONU_STATE -> putOnu(subscriptionId, event)
            PlatformEventTypes.CPE_PROVISIONING -> putCpe(event)
        }
        reevaluate(subscriptionId, event)
    }

    private fun putOpticalBatch(event: PlatformEvent) {
        val persist = opticalBatch.ifAvailable ?: return
        val subscriptionIds = try {
            persist.persistFromEventJson(event.payloadJson)
        } catch (ex: Exception) {
            logger.warn("onu.optical-batch persist failed: {}", ex.message)
            return
        }
        for (subscriptionId in subscriptionIds) {
            reevaluate(subscriptionId, event)
        }
    }

    private fun putCpeInform(event: PlatformEvent) {
        val persist = cpeInform.ifAvailable ?: return
        try {
            persist.persistFromEventJson(event.payloadJson)
        } catch (ex: Exception) {
            logger.warn("cpe.inform persist failed sn={}: {}", event.sn, ex.message)
        }
    }

    private fun putTraffic(subscriptionId: Int, event: PlatformEvent) {
        val node = json.readTree(event.payloadJson)
        liveTelemetry.ifUnique?.putTraffic(
            subscriptionId,
            com.dscorp.wispadmin.events.LiveTrafficSample(
                avgMbpsDown = node.path("mbpsDown").takeIf { it.isNumber }?.asDouble(),
                avgMbpsUp = node.path("mbpsUp").takeIf { it.isNumber }?.asDouble(),
                collectedAt = event.occurredAt,
                sampleStatus = node.path("sampleStatus").asText("OK"),
                hostDeviceId = node.path("hostDeviceId").takeIf { it.isNumber }?.asInt(),
            ),
        )
    }

    private fun putCpe(event: PlatformEvent) {
        val sn = event.sn ?: return
        val status = json.readTree(event.payloadJson).path("cpeStatus").asText(null) ?: return
        cpeFlags.ifAvailable?.apply(sn, status, event.occurredAt, event.eventId)
    }

    private fun putOnu(subscriptionId: Int, event: PlatformEvent) {
        val node = json.readTree(event.payloadJson)
        val sn = event.sn ?: node.path("sn").asText(null) ?: return
        liveTelemetry.ifUnique?.putOnu(
            subscriptionId,
            LiveOnuState(
                sn = sn,
                runState = node.path("runState").asText(null),
                rxPowerDbm = node.path("rxPowerDbm").takeIf { it.isNumber }?.asDouble(),
                observedAt = event.occurredAt,
                updateKind = if (event.type == PlatformEventTypes.ONU_OPTICAL) "optical" else "state",
            ),
        )
    }

    private companion object {
        const val SLOW_REEVALUATE_MS = 250L
    }
}
