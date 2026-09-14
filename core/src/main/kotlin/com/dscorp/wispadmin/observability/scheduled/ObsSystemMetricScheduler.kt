package com.dscorp.wispadmin.observability.scheduled

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsSystemMetric
import com.dscorp.wispadmin.observability.repository.ObsSystemMetricRepository
import com.dscorp.wispadmin.observability.service.ObsLivePublisher
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.lang.management.ManagementFactory
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Component
class ObsSystemMetricScheduler(
    private val registry: MeterRegistry,
    private val systemMetricRepository: ObsSystemMetricRepository,
    private val livePublisher: ObsLivePublisher,
    private val properties: ObservabilityProperties
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    @Scheduled(fixedRateString = "\${observability.system.sample-interval-ms:15000}")
    fun sample() {
        if (!properties.system.enabled) return
        try {
            val now = LocalDateTime.now()
            val bucket = now.withSecond(0).withNano(0)

            val gcPause = registry.find("jvm.gc.pause").timers()
            val gcCount = gcPause.sumOf { it.count() }
            val gcTimeMs = gcPause.sumOf { it.totalTime(TimeUnit.MILLISECONDS) }.toLong()

            val (osFree, osTotal) = readOsMemory()

            val metric = ObsSystemMetric(
                bucketStart = bucket,
                sampledAt = now,
                heapUsedBytes = sumGauge("jvm.memory.used", "area", "heap"),
                heapMaxBytes = sumGauge("jvm.memory.max", "area", "heap"),
                nonHeapUsedBytes = sumGauge("jvm.memory.used", "area", "nonheap"),
                cpuProcess = gauge("process.cpu.usage").coerceAtLeast(0.0),
                cpuSystem = gauge("system.cpu.usage").coerceAtLeast(0.0),
                osMemFreeBytes = osFree,
                osMemTotalBytes = osTotal,
                threadsLive = gauge("jvm.threads.live").toInt(),
                threadsDaemon = gauge("jvm.threads.daemon").toInt(),
                threadsPeak = gauge("jvm.threads.peak").toInt(),
                gcCount = gcCount,
                gcTimeMs = gcTimeMs,
                poolActive = gauge("hikaricp.connections.active").toInt(),
                poolIdle = gauge("hikaricp.connections.idle").toInt(),
                poolWaiting = gauge("hikaricp.connections.pending").toInt(),
                poolTotal = gauge("hikaricp.connections").toInt(),
                poolMax = gauge("hikaricp.connections.max").toInt()
            )

            val saved = systemMetricRepository.save(metric)
            if (properties.system.publishLive) {
                livePublisher.publishSystemMetric(saved)
            }
        } catch (e: Exception) {
            log.warn("Error al muestrear metricas de sistema: {}", e.message)
        }
    }

    private fun gauge(name: String, vararg tags: String): Double {
        return try {
            val value = registry.find(name).tags(*tags).gauges().sumOf { it.value() }
            if (value.isNaN()) 0.0 else value
        } catch (e: Exception) {
            0.0
        }
    }

    private fun sumGauge(name: String, vararg tags: String): Long {
        return gauge(name, *tags).toLong()
    }

    private fun readOsMemory(): Pair<Long, Long> {
        return try {
            val os = ManagementFactory.getOperatingSystemMXBean()
            if (os is com.sun.management.OperatingSystemMXBean) {
                os.freePhysicalMemorySize to os.totalPhysicalMemorySize
            } else {
                0L to 0L
            }
        } catch (e: Throwable) {
            0L to 0L
        }
    }
}
