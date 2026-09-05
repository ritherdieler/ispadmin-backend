package com.dscorp.wispadmin.traffic.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(TrafficProperties::class)
class TrafficConfig

@ConfigurationProperties(prefix = "traffic")
class TrafficProperties {
    var poll: PollProperties = PollProperties()
    var retention: RetentionProperties = RetentionProperties()
    var anomaly: AnomalyProperties = AnomalyProperties()
    var aggregation: AggregationProperties = AggregationProperties()
    var apiKey: String = ""
    var coreBaseUrl: String = ""
    var internalBaseUrl: String = ""
    var clientEnabled: Boolean = false
    var directoryTtlSeconds: Long = 60
    var routerSeed: RouterSeedProperties = RouterSeedProperties()

    fun isValidApiKey(key: String?): Boolean {
        if (apiKey.isBlank() || key.isNullOrBlank()) return false
        return apiKey == key
    }

    class RouterSeedProperties {
        var enabled: Boolean = true
        var sourceSchema: String = ""
    }

    class PollProperties {
        var enabled: Boolean = true
        var intervalMs: Long = 60_000
        var initialDelayMs: Long = 30_000
        var bucketMinutes: Int = 1
        var maxParallelRouters: Int = 3
    }

    class RetentionProperties {
        var rawDays: Int = 3
        var fiveMinuteDays: Int = 30
        var hourlyDays: Int = 730
        var dailyDays: Int = 1825
        var networkHourDays: Int = 400
    }

    class AnomalyProperties {
        var enabled: Boolean = true
        var minimumCoveragePct: Double = 80.0
        var saturationPct: Double = 80.0
        var ruleVersion: String = "traffic-rules-v1"
    }

    class AggregationProperties {
        var catchUpChunkHours: Int = 1
        var oneMinuteSince: String? = null
    }
}
