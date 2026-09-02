package com.dscorp.wispadmin.traffic.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(TrafficProperties::class)
class TrafficConfig

@ConfigurationProperties(prefix = "traffic")
data class TrafficProperties(
    val poll: PollProperties = PollProperties(),
    val retention: RetentionProperties = RetentionProperties(),
    val anomaly: AnomalyProperties = AnomalyProperties(),
    val aggregation: AggregationProperties = AggregationProperties()
) {
    data class PollProperties(
        val enabled: Boolean = true,
        val intervalMs: Long = 60_000,
        val initialDelayMs: Long = 30_000,
        val bucketMinutes: Int = 1,
        val maxParallelRouters: Int = 3
    )

    data class RetentionProperties(
        val rawDays: Int = 3,
        val fiveMinuteDays: Int = 30,
        val hourlyDays: Int = 730,
        val dailyDays: Int = 1825,
        val networkHourDays: Int = 400
    )

    data class AnomalyProperties(
        val enabled: Boolean = true,
        val minimumCoveragePct: Double = 80.0,
        val saturationPct: Double = 80.0,
        val ruleVersion: String = "traffic-rules-v1",
        val evaluationBatchSize: Int = 200
    )

    data class AggregationProperties(
        val catchUpChunkHours: Int = 1,
        val oneMinuteSince: String? = null
    )
}
