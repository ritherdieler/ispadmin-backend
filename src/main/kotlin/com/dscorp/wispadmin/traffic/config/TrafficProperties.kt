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
    val retention: RetentionProperties = RetentionProperties()
) {
    data class PollProperties(
        val enabled: Boolean = true,
        val intervalMs: Long = 300_000,
        val initialDelayMs: Long = 120_000,
        val bucketMinutes: Int = 5
    )

    data class RetentionProperties(
        val rawDays: Int = 14,
        val hourlyDays: Int = 90,
        val dailyDays: Int = 1095,
        val networkHourDays: Int = 400
    )
}
