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
    name = "obs_event",
    indexes = [
        Index(name = "idx_obs_event_issue", columnList = "issue_id"),
        Index(name = "idx_obs_event_fingerprint", columnList = "fingerprint"),
        Index(name = "idx_obs_event_created_at", columnList = "created_at"),
        Index(name = "idx_obs_event_correlation", columnList = "correlation_id"),
        Index(name = "idx_obs_event_session", columnList = "session_id"),
        Index(name = "idx_obs_event_feature", columnList = "feature"),
        Index(name = "idx_obs_event_action", columnList = "action")
    ]
)
data class ObsEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "issue_id")
    var issueId: Long? = null,

    @Column(name = "fingerprint", length = 64)
    var fingerprint: String? = null,

    @Column(name = "event_type", length = 40)
    var eventType: String? = null,

    @Column(name = "platform", length = 60)
    var platform: String? = null,

    @Column(name = "severity", length = 30)
    var severity: String? = null,

    @Column(name = "feature", length = 80)
    var feature: String? = null,

    @Column(name = "action", length = 120)
    var action: String? = null,

    @Lob
    @Column(name = "message", columnDefinition = "TEXT")
    var message: String? = null,

    @Column(name = "error_type", length = 300)
    var errorType: String? = null,

    @Lob
    @Column(name = "stacktrace", columnDefinition = "LONGTEXT")
    var stacktrace: String? = null,

    @Lob
    @Column(name = "stacktrace_symbolicated", columnDefinition = "LONGTEXT")
    var stacktraceSymbolicated: String? = null,

    @Column(name = "environment", length = 60)
    var environment: String? = null,

    @Column(name = "app_release", length = 120)
    var release: String? = null,

    @Column(name = "correlation_id", length = 100)
    var correlationId: String? = null,

    @Column(name = "session_id", length = 100)
    var sessionId: String? = null,

    @Lob
    @Column(name = "url", columnDefinition = "TEXT")
    var url: String? = null,

    @Column(name = "http_method", length = 12)
    var httpMethod: String? = null,

    @Column(name = "http_status")
    var httpStatus: Int? = null,

    @Column(name = "duration_ms")
    var durationMs: Long? = null,

    @Lob
    @Column(name = "user_agent", columnDefinition = "TEXT")
    var userAgent: String? = null,

    @Lob
    @Column(name = "user_json", columnDefinition = "TEXT")
    var userJson: String? = null,

    @Lob
    @Column(name = "device_json", columnDefinition = "TEXT")
    var deviceJson: String? = null,

    @Lob
    @Column(name = "breadcrumbs_json", columnDefinition = "LONGTEXT")
    var breadcrumbsJson: String? = null,

    @Lob
    @Column(name = "tags_json", columnDefinition = "TEXT")
    var tagsJson: String? = null,

    @Lob
    @Column(name = "context_json", columnDefinition = "LONGTEXT")
    var contextJson: String? = null,

    @Column(name = "replay_id")
    var replayId: Long? = null,

    @Column(name = "event_timestamp")
    var eventTimestamp: LocalDateTime? = null,

    @Column(name = "created_at")
    var createdAt: LocalDateTime? = null
)
