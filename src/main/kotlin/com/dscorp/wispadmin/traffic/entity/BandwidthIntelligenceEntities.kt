package com.dscorp.wispadmin.traffic.entity

import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(
    name = "subscription_traffic_five_minute",
    uniqueConstraints = [UniqueConstraint(name = "uk_traffic_5m_sub_bucket", columnNames = ["subscriptionId", "bucketStart"])],
    indexes = [Index(name = "idx_traffic_5m_bucket", columnList = "bucketStart"), Index(name = "idx_traffic_5m_router_bucket", columnList = "hostDeviceId, bucketStart")]
)
data class SubscriptionTrafficFiveMinute(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    var subscriptionId: Int = 0,
    var hostDeviceId: Int = 0,
    var bucketStart: LocalDateTime = LocalDateTime.now(),
    var rxBytesTotal: Long = 0,
    var txBytesTotal: Long = 0,
    var avgMbpsDown: Double = 0.0,
    var avgMbpsUp: Double = 0.0,
    var maxMbpsDown: Double = 0.0,
    var maxMbpsUp: Double = 0.0,
    var p95MbpsDown: Double = 0.0,
    var p95MbpsUp: Double = 0.0,
    var sampleCount: Int = 0,
    var expectedSampleCount: Int = 0,
    var coveragePct: Double = 0.0,
    var planDownloadMbps: Int? = null,
    var planUploadMbps: Int? = null,
    var utilizationDownPct: Double? = null,
    var utilizationUpPct: Double? = null,
    var secondsOver80: Int = 0,
    var secondsOver90: Int = 0,
    var secondsOver95: Int = 0
)

@Entity
@Table(name = "traffic_source_run", indexes = [Index(name = "idx_traffic_source_run_router_time", columnList = "hostDeviceId, startedAt")])
data class TrafficSourceRun(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    var hostDeviceId: Int = 0,
    var startedAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,
    @Enumerated(EnumType.STRING) var status: TrafficSourceRunStatus = TrafficSourceRunStatus.RUNNING,
    var expectedCount: Int = 0,
    var matchedCount: Int = 0,
    var writtenCount: Int = 0,
    var missingCount: Int = 0,
    var invalidCount: Int = 0,
    var durationMs: Long = 0,
    var lagSeconds: Long = 0,
    @Column(length = 500) var errorMessage: String? = null
)

@Entity
@Table(
    name = "traffic_anomaly_event",
    indexes = [
        Index(name = "idx_traffic_anomaly_status_time", columnList = "eventStatus, startedAt"),
        Index(name = "idx_traffic_anomaly_subscription_time", columnList = "subscriptionId, startedAt"),
        Index(name = "idx_traffic_anomaly_router_time", columnList = "hostDeviceId, startedAt")
    ]
)
data class TrafficAnomalyEvent(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "dedupe_key", length = 220, unique = true, nullable = false) var dedupeKey: String = "",
    @Enumerated(EnumType.STRING) var anomalyType: TrafficAnomalyType = TrafficAnomalyType.TRAFFIC_MISSING,
    @Enumerated(EnumType.STRING) var eventStatus: TrafficAnomalyStatus = TrafficAnomalyStatus.OPEN,
    var subscriptionId: Int? = null,
    var hostDeviceId: Int? = null,
    var startedAt: LocalDateTime = LocalDateTime.now(),
    var endedAt: LocalDateTime? = null,
    var lastEvaluatedAt: LocalDateTime = LocalDateTime.now(),
    var baselineValue: Double? = null,
    var observedValue: Double? = null,
    var deviationValue: Double? = null,
    var coveragePct: Double = 0.0,
    var confidence: Double = 0.0,
    @Column(length = 48) var ruleVersion: String = "traffic-rules-v1",
    @Column(columnDefinition = "TEXT") var evidenceJson: String? = null
)
