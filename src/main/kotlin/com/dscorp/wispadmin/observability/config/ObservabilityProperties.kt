package com.dscorp.wispadmin.observability.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "observability")
class ObservabilityProperties {

    var enabled: Boolean = true

    var apiKeys: MutableMap<String, String> = mutableMapOf()

    var readApiPlatforms: MutableList<String> = mutableListOf("dashboard")

    var maxEventsPerBatch: Int = 100

    var maxPayloadBytes: Int = 2_000_000

    var rateLimitPerMinute: Int = 6000

    var internalReportEnabled: Boolean = true

    val replay: ReplayProperties = ReplayProperties()

    val symbols: SymbolsProperties = SymbolsProperties()

    val retention: RetentionProperties = RetentionProperties()

    val jira: JiraProperties = JiraProperties()

    val tracker: TrackerProperties = TrackerProperties()

    val metrics: MetricsProperties = MetricsProperties()

    val rum: RumProperties = RumProperties()

    val system: SystemProperties = SystemProperties()

    val tracing: TracingProperties = TracingProperties()

    val alerts: AlertsProperties = AlertsProperties()

    class AlertsProperties {
        var enabled: Boolean = true
        var evaluationIntervalMs: Long = 60000
        var dedupWindowMinutes: Long = 15
        var dashboardBaseUrl: String = ""
    }

    class ReplayProperties {
        var storageDir: String = "./data/observability/replays"
        var maxUploadBytes: Long = 15_000_000
    }

    class SymbolsProperties {
        var enabled: Boolean = true
        var storageDir: String = "./data/observability/symbols"
        var maxUploadBytes: Long = 60_000_000
    }

    class RetentionProperties {
        var eventDays: Long = 30
        var replayDays: Long = 14
        var metricDays: Long = 7
        var resolvedIssueDays: Long = 90
        var spanDays: Long = 7
        var symbolDays: Long = 120
        var systemMetricDays: Long = 7
        var rumMetricDays: Long = 7
    }

    class TracingProperties {
        var enabled: Boolean = true
        var sampleRate: Double = 1.0
        var maxSpansPerTrace: Int = 300
        var maxBufferedSpans: Int = 20000
    }

    class JiraProperties {
        var enabled: Boolean = false
        var baseUrl: String = ""
        var email: String = ""
        var apiToken: String = ""
        var projectKey: String = ""
        var issueType: String = "Bug"
        var dashboardBaseUrl: String = ""
        var priorityBySeverity: MutableMap<String, String> = mutableMapOf()

        fun isConfigured(): Boolean =
            enabled && baseUrl.isNotBlank() && email.isNotBlank() && apiToken.isNotBlank() && projectKey.isNotBlank()
    }

    class TrackerProperties {
        var provider: String = "jira"
        val webhook: WebhookProperties = WebhookProperties()
        val jira: TrackerJiraProperties = TrackerJiraProperties()

        class WebhookProperties {
            var secret: String = ""
            var allowedIps: MutableList<String> = mutableListOf()
        }

        class TrackerJiraProperties {
            var enabled: Boolean? = null
            var baseUrl: String? = null
            var email: String? = null
            var apiToken: String? = null
            var projectKey: String? = null
            var issueType: String? = null
            var dashboardBaseUrl: String? = null
            var priorityBySeverity: MutableMap<String, String> = mutableMapOf()
            var statusMapping: MutableMap<String, String> = mutableMapOf()
        }
    }

    class MetricsProperties {
        var enabled: Boolean = true
        var maxSamplesPerBucket: Int = 4000
    }

    class RumProperties {
        var enabled: Boolean = true
        var maxSamplesPerBucket: Int = 4000
        var publishLive: Boolean = true
        var allowedMetrics: MutableList<String> = mutableListOf("LCP", "INP", "CLS", "FCP", "TTFB")
    }

    class SystemProperties {
        var enabled: Boolean = true
        var sampleIntervalMs: Long = 15000
        var publishLive: Boolean = true
    }

    fun activeTrackerProvider(): String = tracker.provider.trim().lowercase()

    fun resolvedJira(): ResolvedJiraConfig {
        val t = tracker.jira
        val usingLegacy = t.baseUrl.isNullOrBlank() && jira.baseUrl.isNotBlank()
        return ResolvedJiraConfig(
            enabled = t.enabled ?: jira.enabled,
            baseUrl = (t.baseUrl ?: jira.baseUrl),
            email = (t.email ?: jira.email),
            apiToken = (t.apiToken ?: jira.apiToken),
            projectKey = (t.projectKey ?: jira.projectKey),
            issueType = (t.issueType ?: jira.issueType).ifBlank { "Bug" },
            dashboardBaseUrl = (t.dashboardBaseUrl ?: jira.dashboardBaseUrl),
            priorityBySeverity = if (t.priorityBySeverity.isNotEmpty()) t.priorityBySeverity else jira.priorityBySeverity,
            statusMapping = t.statusMapping,
            usingLegacy = usingLegacy
        )
    }

    fun isValidApiKey(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        return apiKeys.values.any { it.isNotBlank() && it == key }
    }

    fun platformForApiKey(key: String?): String? {
        if (key.isNullOrBlank()) return null
        return apiKeys.entries.firstOrNull { it.value == key }?.key
    }

    fun isReadApiPlatform(platform: String?): Boolean {
        if (platform.isNullOrBlank()) return false
        return readApiPlatforms.any { it.equals(platform, ignoreCase = true) }
    }
}

data class ResolvedJiraConfig(
    val enabled: Boolean,
    val baseUrl: String,
    val email: String,
    val apiToken: String,
    val projectKey: String,
    val issueType: String,
    val dashboardBaseUrl: String,
    val priorityBySeverity: Map<String, String>,
    val statusMapping: Map<String, String>,
    val usingLegacy: Boolean
) {
    fun isConfigured(): Boolean =
        enabled && baseUrl.isNotBlank() && email.isNotBlank() && apiToken.isNotBlank() && projectKey.isNotBlank()
}
