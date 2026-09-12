package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.events.CpeInformPayload
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.Quality
import com.dscorp.wispadmin.servicehealth.domain.TelemetryRun
import com.dscorp.wispadmin.servicehealth.domain.UtcInstantText
import com.dscorp.wispadmin.servicehealth.domain.WifiCurrent
import com.dscorp.wispadmin.servicehealth.domain.WifiStationSample
import com.dscorp.wispadmin.servicehealth.repository.TelemetryRunRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiCountSampleRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiCurrentRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiStationSampleRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class CpeInformPersistService(
    private val identity: IdentityService,
    private val counts: WifiCountSampleRepository,
    private val stations: WifiStationSampleRepository,
    private val current: WifiCurrentRepository,
    private val runs: TelemetryRunRepository,
    private val properties: ServiceHealthProperties,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(CpeInformPersistService::class.java)

    @Transactional
    fun persist(payload: CpeInformPayload) {
        val subscriptionId = identity.resolveOnu(payload.sn)
        if (subscriptionId == null) {
            logger.info("Discarding cpe.inform for unknown SN={}", payload.sn)
            return
        }
        if (!payload.complete || payload.observedAt == null || payload.qualityStatus == "UNSUPPORTED") {
            logger.info(
                "Skipping wifi sample persist for SN={} complete={} quality={}",
                payload.sn,
                payload.complete,
                payload.qualityStatus,
            )
            return
        }
        val existing = counts.findByDeviceIdAndSubscriptionIdAndInformAt(payload.deviceId, subscriptionId, payload.informAt)
        if (existing != null) return
        val now = Instant.now()
        val observedAt = payload.observedAt
        val observedAtText = UtcInstantText.format(observedAt)
        counts.upsertAtomic(
            deviceId = payload.deviceId,
            subscriptionId = subscriptionId,
            informAt = UtcInstantText.format(payload.informAt),
            observedAt = observedAtText,
            collectedAt = UtcInstantText.format(now),
            associatedDeviceCount = payload.associatedDeviceCount,
            associated2g = payload.associated2g,
            associated5g = payload.associated5g,
            lanDeviceCount = payload.lanDeviceCount,
            qualityStatus = payload.qualityStatus,
            sourceRunId = null,
            errorReason = payload.errorReason,
        )
        val countId = AcsWifiSampleLookup.idAfterUpsert(
            counts.findIdByDeviceIdAndSubscriptionIdAndObservedAtSql(payload.deviceId, subscriptionId, observedAtText),
            counts.findByDeviceIdAndSubscriptionIdAndObservedAt(payload.deviceId, subscriptionId, observedAt)?.id,
            counts.findTopByDeviceIdAndSubscriptionIdOrderByInformAtDesc(payload.deviceId, subscriptionId)?.id
                ?: counts.findIdByDeviceIdAndSubscriptionIdAndInformText(
                    payload.deviceId,
                    subscriptionId,
                    UtcInstantText.formatSeconds(payload.informAt).take(19),
                ),
        )
        for (station in payload.stations) {
            val key = try {
                WifiTelemetry.stationKey(properties.stationHmacKey, subscriptionId, station.macNormalized)
            } catch (_: IllegalArgumentException) {
                continue
            }
            stations.save(
                WifiStationSample(
                    countSampleId = countId,
                    subscriptionId = subscriptionId,
                    stationKey = key,
                    band = station.band,
                    displayName = station.displayName,
                    observedAt = station.observedAt,
                    collectedAt = now,
                    rssi = station.rssi,
                    snr = station.snr,
                    noise = station.noise,
                    rxRate = station.rxRate,
                    txRate = station.txRate,
                    packetsTx = station.packetsTx,
                    packetsRx = station.packetsRx,
                )
            )
        }
        val status = current.findById(subscriptionId).orElse(WifiCurrent(subscriptionId = subscriptionId))
        status.deviceId = payload.deviceId
        status.model = payload.model
        status.informAt = payload.informAt
        status.observedAt = payload.observedAt
        status.associatedDeviceCount = payload.associatedDeviceCount
        status.countSampleId = countId
        status.qualityStatus = runCatching { Quality.valueOf(payload.qualityStatus) }.getOrDefault(Quality.FRESH)
        status.updatedAt = now
        current.save(status)
        runs.save(
            TelemetryRun(
                source = "ACS",
                equipmentKey = "gateway-cpe",
                startedAt = payload.informAt,
                completedAt = now,
                qualityStatus = Quality.FRESH,
                readCount = 1,
                writtenCount = 1,
            )
        )
    }

    @Transactional
    fun persistFromEventJson(payloadJson: String) {
        val payload = objectMapper.readValue(payloadJson, CpeInformPayload::class.java)
        persist(payload)
    }
}
