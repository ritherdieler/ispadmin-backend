package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.OpticalDailySample
import com.dscorp.wispadmin.servicehealth.domain.WifiAggregationWatermark
import com.dscorp.wispadmin.servicehealth.repository.HealthCursorRepository
import com.dscorp.wispadmin.servicehealth.repository.OpticalDailySampleRepository
import com.dscorp.wispadmin.servicehealth.repository.OpticalSampleRepository
import com.dscorp.wispadmin.servicehealth.repository.WifiAggregationWatermarkRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class OpticalDailyRollupService(
    private val properties: ServiceHealthProperties,
    private val samples: OpticalSampleRepository,
    private val dailies: OpticalDailySampleRepository,
    private val watermarks: WifiAggregationWatermarkRepository,
    private val cursors: HealthCursorRepository,
) {
    @Scheduled(cron = "0 25 4 * * *")
    @Transactional
    fun scheduled() {
        if (!properties.enabled || !properties.opticalEnabled) return
        catchUp(Instant.now())
    }

    @Transactional
    fun catchUp(now: Instant) {
        if (cursors.lock(LOCK) == null) return
        val closedDay = now.truncatedTo(ChronoUnit.DAYS)
        val watermark = watermarks.findById(LAYER).orElse(null)?.consolidatedThrough
        var from = watermark ?: samples.findTopByOrderByObservedAtAsc()?.observedAt?.truncatedTo(ChronoUnit.DAYS) ?: closedDay
        if (!from.isBefore(closedDay)) return
        while (from.isBefore(closedDay)) {
            val chunkEnd = minOf(from.plus(7, ChronoUnit.DAYS), closedDay)
            rollupRange(from, chunkEnd)
            from = chunkEnd
        }
        val row = watermarks.findById(LAYER).orElse(WifiAggregationWatermark(layer = LAYER))
        row.consolidatedThrough = closedDay
        row.updatedAt = now
        watermarks.save(row)
    }

    private fun rollupRange(from: Instant, to: Instant) {
        val buckets = OpticalDailyAggregator.aggregate(
            samples.findByObservedAtRange(from, to).map { sample ->
                OpticalDailyAggregator.Sample(
                    subscriptionId = sample.subscriptionId,
                    onuId = sample.onuId,
                    onuSn = sample.onuSn,
                    oltId = sample.oltId,
                    board = sample.board,
                    port = sample.port,
                    onuIndex = sample.onuIndex,
                    observedAt = sample.observedAt,
                    onuRxDbm = sample.onuRxDbm,
                    onuTxDbm = sample.onuTxDbm,
                    oltRxDbm = sample.oltRxDbm,
                )
            },
            from,
            to,
        )
        dailies.deleteByBucketStartRange(from, to)
        for (bucket in buckets) {
            dailies.save(
                OpticalDailySample(
                    subscriptionId = bucket.subscriptionId,
                    onuId = bucket.onuId,
                    onuSn = bucket.onuSn,
                    oltId = bucket.oltId,
                    board = bucket.board,
                    port = bucket.port,
                    onuIndex = bucket.onuIndex,
                    bucketStart = bucket.bucketStart,
                    onuRxMin = bucket.onuRxMin,
                    onuRxAvg = bucket.onuRxAvg,
                    onuRxMax = bucket.onuRxMax,
                    onuTxMin = bucket.onuTxMin,
                    onuTxAvg = bucket.onuTxAvg,
                    onuTxMax = bucket.onuTxMax,
                    oltRxMin = bucket.oltRxMin,
                    oltRxAvg = bucket.oltRxAvg,
                    oltRxMax = bucket.oltRxMax,
                    sampleCount = bucket.sampleCount,
                ),
            )
        }
    }

    companion object {
        const val LAYER = "OPTICAL_DAILY"
        const val LOCK = "optical-daily-rollup"
    }
}
