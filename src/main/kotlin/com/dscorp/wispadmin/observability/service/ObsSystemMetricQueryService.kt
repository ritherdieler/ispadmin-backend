package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.SystemMetricPointDto
import com.dscorp.wispadmin.observability.entity.ObsSystemMetric
import com.dscorp.wispadmin.observability.repository.ObsSystemMetricRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ObsSystemMetricQueryService(
    private val systemMetricRepository: ObsSystemMetricRepository
) {

    fun timeSeries(from: LocalDateTime, to: LocalDateTime): List<SystemMetricPointDto> {
        return systemMetricRepository.findByBucketStartBetweenOrderByBucketStartAsc(from, to)
            .map { it.toDto() }
    }

    fun latest(): SystemMetricPointDto? {
        return systemMetricRepository.findFirstByOrderBySampledAtDesc()?.toDto()
    }

    private fun ObsSystemMetric.toDto() = SystemMetricPointDto(
        bucketStart = bucketStart,
        sampledAt = sampledAt,
        heapUsedBytes = heapUsedBytes,
        heapMaxBytes = heapMaxBytes,
        nonHeapUsedBytes = nonHeapUsedBytes,
        cpuProcess = cpuProcess,
        cpuSystem = cpuSystem,
        osMemFreeBytes = osMemFreeBytes,
        osMemTotalBytes = osMemTotalBytes,
        threadsLive = threadsLive,
        threadsDaemon = threadsDaemon,
        threadsPeak = threadsPeak,
        gcCount = gcCount,
        gcTimeMs = gcTimeMs,
        poolActive = poolActive,
        poolIdle = poolIdle,
        poolWaiting = poolWaiting,
        poolTotal = poolTotal,
        poolMax = poolMax
    )
}
