package com.dscorp.wispadmin.observability.entity

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table

@Entity
@Table(
    name = "obs_endpoint_metric",
    indexes = [
        Index(name = "idx_obs_metric_bucket", columnList = "bucket_start"),
        Index(name = "idx_obs_metric_route", columnList = "route"),
        Index(name = "idx_obs_metric_bucket_route", columnList = "bucket_start, route")
    ]
)
data class ObsEndpointMetric(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "bucket_start")
    var bucketStart: LocalDateTime? = null,

    @Column(name = "http_method", length = 12)
    var httpMethod: String? = null,

    @Column(name = "route", length = 300)
    var route: String? = null,

    @Column(name = "sample_count")
    var sampleCount: Long = 0,

    @Column(name = "error_count")
    var errorCount: Long = 0,

    @Column(name = "p50_ms")
    var p50Ms: Long = 0,

    @Column(name = "p95_ms")
    var p95Ms: Long = 0,

    @Column(name = "p99_ms")
    var p99Ms: Long = 0,

    @Column(name = "avg_ms")
    var avgMs: Double = 0.0,

    @Column(name = "max_ms")
    var maxMs: Long = 0,

    @Column(name = "throughput_per_min")
    var throughputPerMin: Long = 0
)
