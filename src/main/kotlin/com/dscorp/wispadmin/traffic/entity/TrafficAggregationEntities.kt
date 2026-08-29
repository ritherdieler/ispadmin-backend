package com.dscorp.wispadmin.traffic.entity

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Index
import javax.persistence.Table

enum class TrafficAggregationLayer { FIVE_MINUTE, HOURLY, DAILY, ONE_MINUTE_SINCE }

enum class TrafficAggregationRunStatus { RUNNING, OK, SKIPPED, FAILED }

@Entity
@Table(name = "traffic_aggregation_watermark")
data class TrafficAggregationWatermark(
    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    var layer: TrafficAggregationLayer = TrafficAggregationLayer.FIVE_MINUTE,

    @Column(name = "consolidated_through")
    var consolidatedThrough: LocalDateTime? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

@Entity
@Table(
    name = "traffic_aggregation_run",
    indexes = [Index(name = "idx_traffic_agg_run_layer_started", columnList = "layer, started_at")]
)
data class TrafficAggregationRun(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    var layer: TrafficAggregationLayer = TrafficAggregationLayer.FIVE_MINUTE,

    @Column(name = "started_at", nullable = false)
    var startedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    var status: TrafficAggregationRunStatus = TrafficAggregationRunStatus.RUNNING,

    @Column(name = "window_from")
    var windowFrom: LocalDateTime? = null,

    @Column(name = "window_to")
    var windowTo: LocalDateTime? = null,

    @Column(name = "rows_read", nullable = false)
    var rowsRead: Int = 0,

    @Column(name = "rows_written", nullable = false)
    var rowsWritten: Int = 0,

    @Column(name = "pending_windows", nullable = false)
    var pendingWindows: Int = 0,

    @Column(name = "error_message", length = 500)
    var errorMessage: String? = null,

    @Column(name = "duration_ms", nullable = false)
    var durationMs: Long = 0
)
