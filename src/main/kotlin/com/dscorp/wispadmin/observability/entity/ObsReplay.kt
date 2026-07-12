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
    name = "obs_replay",
    indexes = [
        Index(name = "idx_obs_replay_session", columnList = "session_id"),
        Index(name = "idx_obs_replay_event", columnList = "event_id"),
        Index(name = "idx_obs_replay_created_at", columnList = "created_at")
    ]
)
data class ObsReplay(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "session_id", length = 100)
    var sessionId: String? = null,

    @Column(name = "event_id")
    var eventId: Long? = null,

    @Column(name = "issue_id")
    var issueId: Long? = null,

    @Column(name = "platform", length = 60)
    var platform: String? = null,

    @Column(name = "format", length = 30)
    var format: String? = "rrweb",

    @Column(name = "file_path", length = 500)
    var filePath: String? = null,

    @Column(name = "content_encoding", length = 30)
    var contentEncoding: String? = null,

    @Column(name = "size_bytes")
    var sizeBytes: Long? = null,

    @Column(name = "duration_ms")
    var durationMs: Long? = null,

    @Column(name = "created_at")
    var createdAt: LocalDateTime? = null
)
