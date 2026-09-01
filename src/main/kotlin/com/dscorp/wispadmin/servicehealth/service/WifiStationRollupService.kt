package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.WifiAggregationWatermark
import com.dscorp.wispadmin.servicehealth.domain.WifiStationHourly
import com.dscorp.wispadmin.servicehealth.repository.HealthCursorRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiAggregationWatermarkRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiStationHourlyRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiStationSampleRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class WifiStationRollupService(
    private val properties: ServiceHealthProperties,
    private val stations: WifiStationSampleRepository,
    private val hourlies: WifiStationHourlyRepository,
    private val watermarks: WifiAggregationWatermarkRepository,
    private val cursors: HealthCursorRepository,
) {
    @Scheduled(cron = "0 10 * * * *")
    @Transactional
    fun scheduled() {
        if (!properties.enabled || !properties.acsEnabled) return
        catchUp(Instant.now())
    }

    @Transactional
    fun catchUp(now: Instant) {
        if (cursors.lock(LOCK) == null) return
        val closedHour = now.truncatedTo(ChronoUnit.HOURS)
        val watermark = watermarks.findById(LAYER).orElse(null)?.consolidatedThrough
        var from = watermark ?: stations.findTopByOrderByObservedAtAsc()?.observedAt?.truncatedTo(ChronoUnit.HOURS) ?: closedHour
        if (!from.isBefore(closedHour)) return
        while (from.isBefore(closedHour)) {
            val chunkEnd = minOf(from.plus(6, ChronoUnit.HOURS), closedHour)
            rollupRange(from, chunkEnd)
            from = chunkEnd
        }
        val row = watermarks.findById(LAYER).orElse(WifiAggregationWatermark(layer = LAYER))
        row.consolidatedThrough = closedHour
        row.updatedAt = now
        watermarks.save(row)
    }

    private fun rollupRange(from: Instant, to: Instant) {
        val buckets = WifiStationHourlyAggregator.aggregate(
            stations.findByObservedAtRange(from, to).map { sample ->
                WifiStationHourlyAggregator.Sample(
                    subscriptionId = sample.subscriptionId,
                    stationKey = sample.stationKey,
                    band = sample.band,
                    observedAt = sample.observedAt,
                    rssi = sample.rssi,
                    snr = sample.snr,
                    displayName = sample.displayName,
                )
            },
            from,
            to,
        )
        hourlies.deleteByBucketStartRange(from, to)
        for (bucket in buckets) {
            hourlies.save(
                WifiStationHourly(
                    subscriptionId = bucket.subscriptionId,
                    stationKey = bucket.stationKey,
                    band = bucket.band,
                    bucketStart = bucket.bucketStart,
                    rssiMin = bucket.rssiMin,
                    rssiAvg = bucket.rssiAvg,
                    rssiMax = bucket.rssiMax,
                    snrMin = bucket.snrMin,
                    snrAvg = bucket.snrAvg,
                    sampleCount = bucket.sampleCount,
                    displayName = bucket.displayName,
                ),
            )
        }
    }

    companion object {
        const val LAYER = "HOURLY"
        const val LOCK = "wifi-hourly-rollup"
    }
}
