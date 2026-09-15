package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.CpeCommandResult
import com.dscorp.wispadmin.acs.CpeStatus
import com.dscorp.wispadmin.acs.entity.CpeRecord
import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import com.dscorp.wispadmin.acs.repository.CpeRecordRepository
import com.dscorp.wispadmin.events.CpeInformPayload
import com.dscorp.wispadmin.events.WifiNbiTelemetry
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class WifiInformNotifyService(
    private val records: CpeRecordRepository,
    private val client: GenieAcsClient,
    private val gateway: AcsToGatewayInformClient,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(WifiInformNotifyService::class.java)
    internal var nowProvider: () -> Instant = { Instant.now() }

    fun notify(deviceId: String?, serial: String?, payload: String? = null): CpeCommandResult {
        val sn = serial?.takeIf { it.isNotBlank() }
            ?: deviceId?.let(WifiNbiTelemetry::snFromDeviceId)
            ?: return CpeCommandResult(false, CpeStatus.FAILED, "missing serial")
        val resolvedDeviceId = deviceId?.takeIf { it.isNotBlank() }
            ?: records.findById(sn).orElse(null)?.deviceId
            ?: return CpeCommandResult(false, CpeStatus.FAILED, "unknown device")
        val record = records.findById(sn).orElseGet {
            CpeRecord(sn = sn, deviceId = resolvedDeviceId, status = CpeStatus.NA)
        }
        if (record.deviceId.isNullOrBlank()) record.deviceId = resolvedDeviceId
        val model = record.productClass
            ?: resolvedDeviceId.split('-').getOrNull(1)
            ?: return CpeCommandResult(false, CpeStatus.FAILED, "unknown model")
        if (record.productClass.isNullOrBlank()) record.productClass = model
        val now = nowProvider()
        val tree = fromInformPayload(payload, sn)
            ?: readNbiCache(resolvedDeviceId)
            ?: return CpeCommandResult(false, CpeStatus.FAILED, "nbi cache miss")
        val inform = WifiNbiTelemetry.parsePayload(tree, model, sn, now)
            ?: return CpeCommandResult(false, CpeStatus.FAILED, "missing lastInform")
        applyLastState(record, inform, now)
        records.save(record)
        try {
            gateway.postInform(inform)
        } catch (ex: Exception) {
            log.warn("Gateway cpe.inform POST failed sn={}: {}", sn, ex.message)
            return CpeCommandResult(false, CpeStatus.FAILED, ex.message)
        }
        return CpeCommandResult(true, CpeStatus.COMPLETE, "inform notified")
    }

    /**
     * The Inform provision already declared these leaves in the session that is
     * notifying us, so this path never touches GenieACS.
     */
    private fun fromInformPayload(payload: String?, sn: String): JsonNode? {
        val raw = payload?.takeIf { it.isNotBlank() } ?: return null
        return try {
            WifiNbiTelemetry.expandInformLeaves(objectMapper.readTree(raw))
        } catch (ex: Exception) {
            log.warn("Inform payload unusable for {}, falling back to NBI: {}", sn, ex.message)
            null
        }
    }

    /**
     * Only for Informs that carried no payload. GenieACS commits the parameter
     * tree when the session closes, so this read sees the previous session.
     */
    private fun readNbiCache(deviceId: String): JsonNode? = try {
        client.readDeviceCache(listOf(deviceId), WifiNbiTelemetry.projection()).firstOrNull()
    } catch (ex: Exception) {
        log.warn("NBI read failed for {}: {}", deviceId, ex.message)
        null
    }

    private fun applyLastState(record: CpeRecord, payload: CpeInformPayload, now: Instant) {
        record.lastInformAt = payload.informAt
        record.wifiSnapshotJson = objectMapper.writeValueAsString(payload)
        record.wifiAssociated2g = payload.associated2g
        record.wifiAssociated5g = payload.associated5g
        record.wifiAssociatedTotal = payload.associatedDeviceCount
        record.wifiObservedAt = payload.observedAt
        record.wifiQualityStatus = payload.qualityStatus
        record.updatedAt = now
    }
}
