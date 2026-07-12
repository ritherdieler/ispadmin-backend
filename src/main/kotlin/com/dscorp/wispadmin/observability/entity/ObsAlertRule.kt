package com.dscorp.wispadmin.observability.entity

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table

enum class ObsAlertType {
    NEW_ISSUE,
    ISSUE_REGRESSION,
    ERROR_SPIKE,
    EVENT_THRESHOLD,
    ENDPOINT_ERROR_RATE,
    ENDPOINT_LATENCY_P95,
    TRACE_ERROR_RATE
}

enum class ObsAlertComparator {
    GT,
    GTE
}

@Entity
@Table(
    name = "obs_alert_rule",
    indexes = [
        Index(name = "idx_obs_alert_rule_enabled", columnList = "enabled"),
        Index(name = "idx_obs_alert_rule_type", columnList = "type")
    ]
)
data class ObsAlertRule(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "name", nullable = false, length = 160)
    var name: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    var type: ObsAlertType = ObsAlertType.NEW_ISSUE,

    @Column(name = "enabled")
    var enabled: Boolean = true,

    @Column(name = "platform", length = 60)
    var platform: String? = null,

    @Column(name = "severity", length = 30)
    var severity: String? = null,

    @Column(name = "environment", length = 60)
    var environment: String? = null,

    @Column(name = "route_pattern", length = 300)
    var routePattern: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "comparator", length = 8)
    var comparator: ObsAlertComparator = ObsAlertComparator.GTE,

    @Column(name = "threshold")
    var threshold: Double = 0.0,

    @Column(name = "window_minutes")
    var windowMinutes: Int = 5,

    @Column(name = "baseline_multiplier")
    var baselineMultiplier: Double = 3.0,

    @Column(name = "min_sample")
    var minSample: Long = 0,

    @Column(name = "channel_ids", length = 300)
    var channelIds: String? = null,

    @Column(name = "last_triggered_at")
    var lastTriggeredAt: LocalDateTime? = null,

    @Column(name = "created_at")
    var createdAt: LocalDateTime? = null,

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null
)
