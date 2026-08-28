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
import javax.persistence.Table

@Entity
@Table(
    name = "subscription_traffic_sample",
    indexes = [
        Index(name = "idx_traffic_sample_bucket", columnList = "bucket_start"),
        Index(name = "idx_traffic_sample_sub_bucket", columnList = "subscription_id, bucket_start")
    ]
)
data class SubscriptionTrafficSample(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "subscription_id", nullable = false)
    var subscriptionId: Int = 0,

    @Column(name = "host_device_id", nullable = false)
    var hostDeviceId: Int = 0,

    @Column(name = "bucket_start", nullable = false)
    var bucketStart: LocalDateTime = LocalDateTime.now(),

    @Column(name = "rx_bytes_delta", nullable = false)
    var rxBytesDelta: Long = 0,

    @Column(name = "tx_bytes_delta", nullable = false)
    var txBytesDelta: Long = 0,

    @Column(name = "avg_mbps_down", nullable = false)
    var avgMbpsDown: Double = 0.0,

    @Column(name = "avg_mbps_up", nullable = false)
    var avgMbpsUp: Double = 0.0,

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
    var sampleCount: Int = 0
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
    var activeHours: Int = 0
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
    var activeDays: Int = 0
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
