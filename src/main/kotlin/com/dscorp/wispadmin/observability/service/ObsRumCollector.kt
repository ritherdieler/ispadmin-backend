package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsRumMetric
import com.dscorp.wispadmin.observability.repository.ObsRumMetricRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Service
class ObsRumCollector(
    private val rumMetricRepository: ObsRumMetricRepository,
    private val livePublisher: ObsLivePublisher,
    private val properties: ObservabilityProperties
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val buckets = ConcurrentHashMap<BucketKey, Accumulator>()

    fun record(page: String, platform: String, metricName: String, value: Double, rating: String?) {
        if (!properties.enabled || !properties.rum.enabled) return
        val minute = System.currentTimeMillis() / 60000
        val key = BucketKey(minute, page, platform, metricName)
        val accumulator = buckets.computeIfAbsent(key) { Accumulator(properties.rum.maxSamplesPerBucket) }
        accumulator.add(value, resolveRating(metricName, value, rating))
    }

    fun flush() {
        if (buckets.isEmpty()) return
        val currentMinute = System.currentTimeMillis() / 60000
        val keys = buckets.keys.filter { it.minute < currentMinute }
        val metrics = ArrayList<ObsRumMetric>()
        for (key in keys) {
            val accumulator = buckets.remove(key) ?: continue
            val snapshot = accumulator.snapshot()
            if (snapshot.isEmpty()) continue
            metrics.add(
                ObsRumMetric(
                    bucketStart = toBucketStart(key.minute),
                    page = key.page.take(300),
                    platform = key.platform.take(40),
                    metricName = key.metricName.take(16),
                    sampleCount = snapshot.count,
                    p50 = snapshot.percentile(50.0),
                    p75 = snapshot.percentile(75.0),
                    p95 = snapshot.percentile(95.0),
                    p99 = snapshot.percentile(99.0),
                    min = snapshot.min,
                    max = snapshot.max,
                    avg = snapshot.avg,
                    goodCount = snapshot.goodCount,
                    needsImprovementCount = snapshot.needsImprovementCount,
                    poorCount = snapshot.poorCount
                )
            )
        }
        if (metrics.isNotEmpty()) {
            try {
                val saved = rumMetricRepository.saveAll(metrics)
                if (properties.rum.publishLive) {
                    saved.forEach { livePublisher.publishRumMetric(it) }
                }
            } catch (e: Exception) {
                log.warn("No se pudieron persistir metricas RUM: {}", e.message)
            }
        }
    }

    private fun resolveRating(metricName: String, value: Double, rating: String?): Rating {
        val normalized = rating?.trim()?.lowercase()
        return when (normalized) {
            "good" -> Rating.GOOD
            "needs-improvement", "needsimprovement", "needs_improvement" -> Rating.NEEDS_IMPROVEMENT
            "poor" -> Rating.POOR
            else -> classify(metricName, value)
        }
    }

    private fun classify(metricName: String, value: Double): Rating {
        val (goodMax, poorMin) = when (metricName.uppercase()) {
            "LCP" -> 2500.0 to 4000.0
            "INP" -> 200.0 to 500.0
            "CLS" -> 0.1 to 0.25
            "FCP" -> 1800.0 to 3000.0
            "TTFB" -> 800.0 to 1800.0
            else -> return Rating.NEEDS_IMPROVEMENT
        }
        return when {
            value <= goodMax -> Rating.GOOD
            value <= poorMin -> Rating.NEEDS_IMPROVEMENT
            else -> Rating.POOR
        }
    }

    private fun toBucketStart(minute: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(minute * 60000), ZoneId.systemDefault())

    private enum class Rating { GOOD, NEEDS_IMPROVEMENT, POOR }

    private data class BucketKey(
        val minute: Long,
        val page: String,
        val platform: String,
        val metricName: String
    )

    private class Accumulator(private val maxSamples: Int) {
        private val samples = ArrayList<Double>()
        private val count = AtomicLong(0)
        private val goodCount = AtomicLong(0)
        private val needsImprovementCount = AtomicLong(0)
        private val poorCount = AtomicLong(0)
        private var min = Double.MAX_VALUE
        private var max = 0.0

        @Synchronized
        fun add(value: Double, rating: Rating) {
            count.incrementAndGet()
            when (rating) {
                Rating.GOOD -> goodCount.incrementAndGet()
                Rating.NEEDS_IMPROVEMENT -> needsImprovementCount.incrementAndGet()
                Rating.POOR -> poorCount.incrementAndGet()
            }
            if (value > max) max = value
            if (value < min) min = value
            if (samples.size < maxSamples) samples.add(value)
        }

        @Synchronized
        fun snapshot(): Snapshot {
            val sorted = samples.toDoubleArray()
            sorted.sort()
            val total = sorted.sum()
            val avg = if (sorted.isNotEmpty()) total / sorted.size else 0.0
            val minValue = if (min == Double.MAX_VALUE) 0.0 else min
            return Snapshot(
                count.get(),
                goodCount.get(),
                needsImprovementCount.get(),
                poorCount.get(),
                minValue,
                max,
                avg,
                sorted
            )
        }
    }

    private class Snapshot(
        val count: Long,
        val goodCount: Long,
        val needsImprovementCount: Long,
        val poorCount: Long,
        val min: Double,
        val max: Double,
        val avg: Double,
        private val sortedSamples: DoubleArray
    ) {
        fun isEmpty(): Boolean = count == 0L

        fun percentile(p: Double): Double {
            if (sortedSamples.isEmpty()) return 0.0
            val rank = Math.ceil((p / 100.0) * sortedSamples.size).toInt().coerceIn(1, sortedSamples.size)
            return sortedSamples[rank - 1]
        }
    }
}
