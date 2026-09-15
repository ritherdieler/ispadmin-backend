package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.events.CpeInformPayload
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthScope
import com.dscorp.wispadmin.servicehealth.domain.Quality
import com.dscorp.wispadmin.servicehealth.domain.UtcInstantText
import com.dscorp.wispadmin.servicehealth.domain.WifiCurrent
import com.dscorp.wispadmin.servicehealth.domain.WifiStationSample
import com.dscorp.wispadmin.servicehealth.port.AcsSubscriptionPort
import com.dscorp.wispadmin.servicehealth.repository.WifiCountSampleRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiCurrentRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class CpeInformPersistService(
    private val identity: IdentityService,
    private val counts: WifiCountSampleRepository,
    private val stationWriter: WifiStationSampleWriter,
    private val current: WifiCurrentRepository,
    private val scope: ServiceHealthScope,
    private val properties: ServiceHealthProperties,
    private val objectMapper: ObjectMapper,
    private val acsRegistry: AcsSubscriptionPort,
) {
    private val logger = LoggerFactory.getLogger(CpeInformPersistService::class.java)

    @Transactional
    fun persist(payload: CpeInformPayload) {
        val subscriptionId = identity.resolveOnu(payload.sn)
        if (subscriptionId == null) {
            logger.info("Discarding cpe.inform for unknown SN={}", payload.sn)
            return
        }
        // Every ONU informs on its own interval whether or not this environment
        // is meant to collect it, so the scope gate belongs here and not only on
        // the (now gone) poll.
        if (!scope.collects(subscriptionId)) {
            logger.debug("Out of collection scope, skipping cpe.inform for subscription={}", subscriptionId)
            return
        }
        val observedAt = payload.observedAt
        if (!payload.complete || observedAt == null || payload.qualityStatus == "UNSUPPORTED") {
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
        stationWriter.insertAll(
            payload.stations.mapNotNull { station ->
                val key = try {
                    WifiTelemetry.stationKey(properties.stationHmacKey, subscriptionId, station.macNormalized)
                } catch (_: IllegalArgumentException) {
                    return@mapNotNull null
                }
                WifiStationSample(
                    countSampleId = countId,
                    subscriptionId = subscriptionId,
                    stationKey = key,
                    band = station.band,
                    displayName = station.displayName,
                    observedAt = payload.informAt,
                    collectedAt = now,
                    rssi = station.rssi,
                    snr = station.snr,
                    noise = station.noise,
                    rxRate = station.rxRate,
                    txRate = station.txRate,
                    packetsTx = station.packetsTx,
                    packetsRx = station.packetsRx,
                )
            },
        )
        refreshLive(subscriptionId, payload, countId, now)
    }

    @Transactional
    fun persistFromEventJson(payloadJson: String) {
        val payload = objectMapper.readValue(payloadJson, CpeInformPayload::class.java)
        persist(payload)
    }

    private fun refreshLive(subscriptionId: Int, payload: CpeInformPayload, countSampleId: Long?, now: Instant) {
        val status = current.findById(subscriptionId).orElse(WifiCurrent(subscriptionId = subscriptionId))
        status.deviceId = payload.deviceId
        status.model = payload.model
        status.informAt = payload.informAt
        status.observedAt = payload.observedAt
        status.associatedDeviceCount = payload.associatedDeviceCount
        status.countSampleId = countSampleId
        status.qualityStatus = runCatching { Quality.valueOf(payload.qualityStatus) }.getOrDefault(Quality.FRESH)
        status.updatedAt = now
        current.save(status)
        acsRegistry.recordInform(
            subscriptionId,
            LocalDateTime.ofInstant(payload.informAt, ZoneOffset.UTC),
            payload.model.orEmpty(),
            "",
            LocalDateTime.ofInstant(now, ZoneOffset.UTC),
        )
    }
}
