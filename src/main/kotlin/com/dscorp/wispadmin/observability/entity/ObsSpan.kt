package com.dscorp.wispadmin.observability.entity

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Lob
import javax.persistence.Table

@Entity
@Table(
    name = "obs_span",
    indexes = [
        Index(name = "idx_obs_span_trace", columnList = "trace_id"),
        Index(name = "idx_obs_span_parent", columnList = "parent_span_id"),
        Index(name = "idx_obs_span_start", columnList = "start_epoch_ms"),
        Index(name = "idx_obs_span_session", columnList = "session_id")
    ]
)
data class ObsSpan(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "trace_id", length = 32)
    var traceId: String? = null,

    @Column(name = "span_id", length = 16)
    var spanId: String? = null,

    @Column(name = "parent_span_id", length = 16)
    var parentSpanId: String? = null,

    @Column(name = "name", length = 500)
    var name: String? = null,

    @Column(name = "kind", length = 20)
    var kind: String? = null,

    @Column(name = "platform", length = 60)
    var platform: String? = null,

    @Column(name = "session_id", length = 100)
    var sessionId: String? = null,

    @Column(name = "start_epoch_ms")
    var startEpochMs: Long? = null,

    @Column(name = "duration_ms")
    var durationMs: Long? = null,

    @Column(name = "status", length = 10)
    var status: String? = null,

    @Column(name = "http_method", length = 12)
    var httpMethod: String? = null,

    @Column(name = "http_route", length = 500)
    var httpRoute: String? = null,

    @Column(name = "http_status")
    var httpStatus: Int? = null,

    @Lob
    @Column(name = "db_statement", columnDefinition = "TEXT")
    var dbStatement: String? = null,

    @Lob
    @Column(name = "tags_json", columnDefinition = "TEXT")
    var tagsJson: String? = null,

    @Column(name = "environment", length = 60)
    var environment: String? = null,

    @Column(name = "app_release", length = 120)
    var release: String? = null,

    @Column(name = "created_at")
    var createdAt: LocalDateTime? = null
)
