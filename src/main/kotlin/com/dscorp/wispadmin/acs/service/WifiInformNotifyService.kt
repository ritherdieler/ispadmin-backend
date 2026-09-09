package com.dscorp.wispadmin.acs.service

import com.dscorp.wispadmin.acs.CpeCommandResult
import com.dscorp.wispadmin.acs.CpeStatus
import com.dscorp.wispadmin.acs.entity.CpeRecord
import com.dscorp.wispadmin.acs.genieacs.GenieAcsClient
import com.dscorp.wispadmin.acs.repository.CpeRecordRepository
import com.dscorp.wispadmin.events.CpeInformPayload
import com.dscorp.wispadmin.events.WifiNbiTelemetry
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

    fun notify(deviceId: String?, serial: String?): CpeCommandResult {
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
        val tree = try {
            client.readDeviceCache(listOf(resolvedDeviceId), WifiNbiTelemetry.projection()).firstOrNull()
        } catch (ex: Exception) {
            log.warn("NBI read failed for {}: {}", resolvedDeviceId, ex.message)
            null
        } ?: return CpeCommandResult(false, CpeStatus.FAILED, "nbi cache miss")
        val payload = WifiNbiTelemetry.parsePayload(tree, model, sn, now)
            ?: return CpeCommandResult(false, CpeStatus.FAILED, "missing lastInform")
        applyLastState(record, payload, now)
        records.save(record)
        try {
            gateway.postInform(payload)
        } catch (ex: Exception) {
            log.warn("Gateway cpe.inform POST failed sn={}: {}", sn, ex.message)
            return CpeCommandResult(false, CpeStatus.FAILED, ex.message)
        }
        return CpeCommandResult(true, CpeStatus.COMPLETE, "inform notified")
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
