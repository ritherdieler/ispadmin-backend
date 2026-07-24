package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.RumMetricAggregateDto
import com.dscorp.wispadmin.observability.dto.RumMetricPointDto
import com.dscorp.wispadmin.observability.repository.ObsRumMetricRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ObsRumQueryService(
    private val rumMetricRepository: ObsRumMetricRepository
) {

    fun timeSeries(
        from: LocalDateTime,
        to: LocalDateTime,
        page: String?,
        metricName: String?,
        release: String? = null
    ): List<RumMetricPointDto> {
        val normalizedMetric = metricName?.takeIf { it.isNotBlank() }?.uppercase()
        val normalizedRelease = release?.takeIf { it.isNotBlank() }
        return rumMetricRepository.findByBucketStartBetweenOrderByBucketStartAsc(from, to)
            .filter { page.isNullOrBlank() || it.page == page }
            .filter { normalizedMetric == null || it.metricName == normalizedMetric }
            .filter { normalizedRelease == null || it.release == normalizedRelease }
            .map {
                RumMetricPointDto(
                    bucketStart = it.bucketStart,
                    page = it.page,
                    platform = it.platform,
                    metricName = it.metricName,
                    release = it.release,
                    sampleCount = it.sampleCount,
                    p50 = it.p50,
                    p75 = it.p75,
                    p95 = it.p95,
                    p99 = it.p99,
                    min = it.min,
                    max = it.max,
                    avg = it.avg,
                    goodCount = it.goodCount,
                    needsImprovementCount = it.needsImprovementCount,
                    poorCount = it.poorCount
                )
            }
    }

    fun aggregate(from: LocalDateTime, to: LocalDateTime, release: String? = null): List<RumMetricAggregateDto> {
        val normalizedRelease = release?.takeIf { it.isNotBlank() }
        return rumMetricRepository.aggregateByPageAndMetric(from, to, normalizedRelease).map {
            val page = it[0]?.toString()
            val metricName = it[1]?.toString()
            val sampleCount = (it[2] as Number).toLong()
            val goodCount = (it[3] as Number).toLong()
            val needsImprovementCount = (it[4] as Number).toLong()
            val poorCount = (it[5] as Number).toLong()
            val avg = (it[6] as Number).toDouble()
            val p75 = (it[7] as Number).toDouble()
            val p95 = (it[8] as Number).toDouble()
            val p99 = (it[9] as Number).toDouble()
            val max = (it[10] as Number).toDouble()
            val total = if (sampleCount > 0) sampleCount.toDouble() else 1.0
            RumMetricAggregateDto(
                page = page,
                metricName = metricName,
                release = normalizedRelease,
                sampleCount = sampleCount,
                goodCount = goodCount,
                needsImprovementCount = needsImprovementCount,
                poorCount = poorCount,
                goodRate = goodCount / total,
                needsImprovementRate = needsImprovementCount / total,
                poorRate = poorCount / total,
                avg = avg,
                p75 = p75,
                p95 = p95,
                p99 = p99,
                max = max,
                rating = rate(metricName, p75)
            )
        }
    }

    private fun rate(metricName: String?, value: Double): String {
        val (goodMax, poorMin) = when (metricName?.uppercase()) {
            "LCP" -> 2500.0 to 4000.0
            "INP" -> 200.0 to 500.0
            "CLS" -> 0.1 to 0.25
            "FCP" -> 1800.0 to 3000.0
            "TTFB" -> 800.0 to 1800.0
            else -> return "needs-improvement"
        }
        return when {
            value <= goodMax -> "good"
            value <= poorMin -> "needs-improvement"
            else -> "poor"
        }
    }
}
