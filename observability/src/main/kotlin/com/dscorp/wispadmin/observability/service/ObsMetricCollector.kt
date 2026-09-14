package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsEndpointMetric
import com.dscorp.wispadmin.observability.repository.ObsEndpointMetricRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Service
class ObsMetricCollector(
    private val metricRepository: ObsEndpointMetricRepository,
    private val properties: ObservabilityProperties
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val buckets = ConcurrentHashMap<BucketKey, Accumulator>()

    fun record(method: String, route: String, durationMs: Long, status: Int) {
        if (!properties.enabled || !properties.metrics.enabled) return
        val minute = System.currentTimeMillis() / 60000
        val key = BucketKey(minute, method, route)
        val accumulator = buckets.computeIfAbsent(key) { Accumulator(properties.metrics.maxSamplesPerBucket) }
        accumulator.add(durationMs, status)
    }

    fun flush() {
        if (buckets.isEmpty()) return
        val currentMinute = System.currentTimeMillis() / 60000
        val keys = buckets.keys.filter { it.minute < currentMinute }
        val metrics = ArrayList<ObsEndpointMetric>()
        for (key in keys) {
            val accumulator = buckets.remove(key) ?: continue
            val snapshot = accumulator.snapshot()
            if (snapshot.isEmpty()) continue
            metrics.add(
                ObsEndpointMetric(
                    bucketStart = toBucketStart(key.minute),
                    httpMethod = key.method,
                    route = key.route.take(300),
                    sampleCount = snapshot.count,
                    errorCount = snapshot.errorCount,
                    p50Ms = snapshot.percentile(50.0),
                    p95Ms = snapshot.percentile(95.0),
                    p99Ms = snapshot.percentile(99.0),
                    avgMs = snapshot.avg,
                    maxMs = snapshot.max,
                    throughputPerMin = snapshot.count
                )
            )
        }
        if (metrics.isNotEmpty()) {
            try {
                metricRepository.saveAll(metrics)
            } catch (e: Exception) {
                log.warn("No se pudieron persistir metricas APM: {}", e.message)
            }
        }
    }

    private fun toBucketStart(minute: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(minute * 60000), ZoneId.systemDefault())

    private data class BucketKey(val minute: Long, val method: String, val route: String)

    private class Accumulator(private val maxSamples: Int) {
        private val samples = ArrayList<Long>()
        private val count = AtomicLong(0)
        private val errorCount = AtomicLong(0)
        private var max = 0L

        @Synchronized
        fun add(durationMs: Long, status: Int) {
            count.incrementAndGet()
            if (status >= 400) errorCount.incrementAndGet()
            if (durationMs > max) max = durationMs
            if (samples.size < maxSamples) samples.add(durationMs)
        }

        @Synchronized
        fun snapshot(): Snapshot {
            val sorted = samples.toLongArray()
            sorted.sort()
            val total = sorted.sum()
            val avg = if (sorted.isNotEmpty()) total.toDouble() / sorted.size else 0.0
            return Snapshot(count.get(), errorCount.get(), max, avg, sorted)
        }
    }

    private class Snapshot(
        val count: Long,
        val errorCount: Long,
        val max: Long,
        val avg: Double,
        private val sortedSamples: LongArray
    ) {
        fun isEmpty(): Boolean = count == 0L

        fun percentile(p: Double): Long {
            if (sortedSamples.isEmpty()) return 0
            val rank = Math.ceil((p / 100.0) * sortedSamples.size).toInt().coerceIn(1, sortedSamples.size)
            return sortedSamples[rank - 1]
        }
    }
}
