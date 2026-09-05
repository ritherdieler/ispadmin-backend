package com.dscorp.wispadmin.traffic.entity

import java.time.LocalDate
import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.Index
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Table
import javax.persistence.UniqueConstraint

enum class TrafficSampleStatus { OK, BASELINE, MISSING, RESET, INVALID, STALE, UNSUPPORTED }
enum class TrafficSourceRunStatus { RUNNING, OK, PARTIAL, FAILED }
enum class TrafficAnomalyType { TRAFFIC_MISSING, NO_TRAFFIC, PLAN_SATURATION, TRAFFIC_SPIKE, TRAFFIC_DROP, PATTERN_DEVIATION }
enum class TrafficAnomalyStatus { OPEN, CLOSED }

@Entity
@Table(
    name = "subscription_traffic_sample",
    uniqueConstraints = [UniqueConstraint(name = "uk_traffic_sample_ip_bucket", columnNames = ["client_ip", "bucket_start"])],
    indexes = [
        Index(name = "idx_traffic_sample_bucket", columnList = "bucket_start"),
        Index(name = "idx_traffic_sample_ip_bucket", columnList = "client_ip, bucket_start"),
        Index(name = "idx_traffic_sample_sub_bucket", columnList = "subscription_id, bucket_start")
    ]
)
data class SubscriptionTrafficSample(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "client_ip", length = 45, nullable = false)
    var clientIp: String = "",

    @Column(name = "subscription_id")
    var subscriptionId: Int? = null,

    @Column(name = "host_device_id", nullable = false)
    var hostDeviceId: Int = 0,

    @Column(name = "bucket_start", nullable = false)
    var bucketStart: LocalDateTime = LocalDateTime.now(),

    @Column(name = "collected_at")
    var collectedAt: LocalDateTime? = null,

    @Column(name = "interval_seconds")
    var intervalSeconds: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "sample_status", nullable = false, length = 24)
    var sampleStatus: TrafficSampleStatus = TrafficSampleStatus.OK,

    @Column(name = "error_reason", length = 255)
    var errorReason: String? = null,

    @Column(name = "source_run_id")
    var sourceRunId: Long? = null,

    @Column(name = "queue_id", length = 96)
    var queueId: String? = null,

    @Column(name = "queue_name", length = 160)
    var queueName: String? = null,

    @Column(name = "plan_download_mbps")
    var planDownloadMbps: Int? = null,

    @Column(name = "plan_upload_mbps")
    var planUploadMbps: Int? = null,

    @Column(name = "rx_bytes_delta")
    var rxBytesDelta: Long? = null,

    @Column(name = "tx_bytes_delta")
    var txBytesDelta: Long? = null,

    @Column(name = "avg_mbps_down")
    var avgMbpsDown: Double? = null,

    @Column(name = "avg_mbps_up")
    var avgMbpsUp: Double? = null,

    @Column(name = "counter_reset", nullable = false)
    var counterReset: Boolean = false
)

@Entity
@Table(
    name = "subscription_traffic_hourly",
    indexes = [
        Index(name = "idx_traffic_hourly_bucket", columnList = "bucket_start"),
        Index(name = "idx_traffic_hourly_sub_bucket", columnList = "subscription_id, bucket_start")
    ]
)
data class SubscriptionTrafficHourly(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "subscription_id", nullable = false)
    var subscriptionId: Int = 0,

    @Column(name = "bucket_start", nullable = false)
    var bucketStart: LocalDateTime = LocalDateTime.now(),

    @Column(name = "rx_bytes_total", nullable = false)
    var rxBytesTotal: Long = 0,

    @Column(name = "tx_bytes_total", nullable = false)
    var txBytesTotal: Long = 0,

    @Column(name = "max_mbps_down", nullable = false)
    var maxMbpsDown: Double = 0.0,

    @Column(name = "max_mbps_up", nullable = false)
    var maxMbpsUp: Double = 0.0,

    @Column(name = "p95_mbps_down", nullable = false)
    var p95MbpsDown: Double = 0.0,

    @Column(name = "p95_mbps_up", nullable = false)
    var p95MbpsUp: Double = 0.0,

    @Column(name = "sample_count", nullable = false)
    var sampleCount: Int = 0,
    var avgMbpsDown: Double = 0.0,
    var avgMbpsUp: Double = 0.0,
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
@Table(
    name = "subscription_traffic_daily",
    indexes = [
        Index(name = "idx_traffic_daily_bucket", columnList = "bucket_start"),
        Index(name = "idx_traffic_daily_sub_bucket", columnList = "subscription_id, bucket_start")
    ]
)
data class SubscriptionTrafficDaily(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "subscription_id", nullable = false)
    var subscriptionId: Int = 0,

    @Column(name = "bucket_start", nullable = false)
    var bucketStart: LocalDate = LocalDate.now(),

    @Column(name = "rx_bytes_total", nullable = false)
    var rxBytesTotal: Long = 0,

    @Column(name = "tx_bytes_total", nullable = false)
    var txBytesTotal: Long = 0,

    @Column(name = "max_mbps_down", nullable = false)
    var maxMbpsDown: Double = 0.0,

    @Column(name = "max_mbps_up", nullable = false)
    var maxMbpsUp: Double = 0.0,

    @Column(name = "p95_mbps_down", nullable = false)
    var p95MbpsDown: Double = 0.0,

    @Column(name = "p95_mbps_up", nullable = false)
    var p95MbpsUp: Double = 0.0,

    @Column(name = "active_hours", nullable = false)
    var activeHours: Int = 0,
    var avgMbpsDown: Double = 0.0,
    var avgMbpsUp: Double = 0.0,
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
@Table(
    name = "subscription_traffic_monthly",
    indexes = [Index(name = "idx_traffic_monthly_month", columnList = "month_key")]
)
data class SubscriptionTrafficMonthly(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "subscription_id", nullable = false)
    var subscriptionId: Int = 0,

    @Column(name = "month_key", nullable = false, length = 7)
    var yearMonth: String = "",

    @Column(name = "rx_bytes_total", nullable = false)
    var rxBytesTotal: Long = 0,

    @Column(name = "tx_bytes_total", nullable = false)
    var txBytesTotal: Long = 0,

    @Column(name = "max_mbps_down", nullable = false)
    var maxMbpsDown: Double = 0.0,

    @Column(name = "max_mbps_up", nullable = false)
    var maxMbpsUp: Double = 0.0,

    @Column(name = "p95_mbps_down", nullable = false)
    var p95MbpsDown: Double = 0.0,

    @Column(name = "p95_mbps_up", nullable = false)
    var p95MbpsUp: Double = 0.0,

    @Column(name = "active_days", nullable = false)
    var activeDays: Int = 0,
    var avgMbpsDown: Double = 0.0,
    var avgMbpsUp: Double = 0.0,
    var sampleCount: Int = 0,
    var expectedSampleCount: Int = 0,
    var coveragePct: Double = 0.0,
    var planDownloadMbps: Int? = null,
    var planUploadMbps: Int? = null,
    var utilizationDownPct: Double? = null,
    var utilizationUpPct: Double? = null
)

@Entity
@Table(name = "subscription_traffic_counter_state")
data class SubscriptionTrafficCounterState(
    @Id
    @Column(name = "subscription_id")
    var subscriptionId: Int = 0,

    @Column(name = "host_device_id", nullable = false)
    var hostDeviceId: Int = 0,

    @Column(name = "last_rx_bytes", nullable = false)
    var lastRxBytes: Long = 0,

    @Column(name = "last_tx_bytes", nullable = false)
    var lastTxBytes: Long = 0,

    @Column(name = "last_router_uptime_seconds")
    var lastRouterUptimeSeconds: Long? = null,

    @Column(name = "last_polled_at")
    var lastPolledAt: LocalDateTime? = null
)

@Entity
@Table(
    name = "network_traffic_hour_of_day",
    indexes = [Index(name = "idx_network_traffic_hour_date", columnList = "bucket_date")]
)
@IdClass(NetworkTrafficHourOfDayId::class)
data class NetworkTrafficHourOfDay(
    @Id
    @Column(name = "bucket_date", nullable = false)
    var bucketDate: LocalDate = LocalDate.now(),

    @Id
    @Column(name = "hour_of_day", nullable = false)
    var hourOfDay: Int = 0,

    @Column(name = "rx_bytes_total", nullable = false)
    var rxBytesTotal: Long = 0,

    @Column(name = "tx_bytes_total", nullable = false)
    var txBytesTotal: Long = 0,

    @Column(name = "active_subscriptions", nullable = false)
    var activeSubscriptions: Int = 0
)

data class NetworkTrafficHourOfDayId(
    var bucketDate: LocalDate = LocalDate.now(),
    var hourOfDay: Int = 0
) : java.io.Serializable
