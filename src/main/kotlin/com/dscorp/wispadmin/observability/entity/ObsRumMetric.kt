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
    name = "obs_rum_metric",
    indexes = [
        Index(name = "idx_obs_rum_bucket", columnList = "bucket_start"),
        Index(name = "idx_obs_rum_page", columnList = "page"),
        Index(name = "idx_obs_rum_bucket_page_platform_metric", columnList = "bucket_start, page, platform, metric_name")
    ]
)
data class ObsRumMetric(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "bucket_start")
    var bucketStart: LocalDateTime? = null,

    @Column(name = "page", length = 300)
    var page: String? = null,

    @Column(name = "platform", length = 40)
    var platform: String? = null,

    @Column(name = "metric_name", length = 16)
    var metricName: String? = null,

    @Column(name = "app_release", length = 120)
    var release: String? = null,

    @Column(name = "sample_count")
    var sampleCount: Long = 0,

    @Column(name = "p50")
    var p50: Double = 0.0,

    @Column(name = "p75")
    var p75: Double = 0.0,

    @Column(name = "p95")
    var p95: Double = 0.0,

    @Column(name = "p99")
    var p99: Double = 0.0,

    @Column(name = "min_value")
    var min: Double = 0.0,

    @Column(name = "max_value")
    var max: Double = 0.0,

    @Column(name = "avg_value")
    var avg: Double = 0.0,

    @Column(name = "good_count")
    var goodCount: Long = 0,

    @Column(name = "needs_improvement_count")
    var needsImprovementCount: Long = 0,

    @Column(name = "poor_count")
    var poorCount: Long = 0
)
