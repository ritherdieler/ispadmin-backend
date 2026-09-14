package com.dscorp.wispadmin.oltgateway.domain.entity

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "olt_mgr_sync_run")
class OltMgrSyncRun(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "started_at", nullable = false)
    var startedAt: Instant = Instant.now(),

    @Column(name = "finished_at")
    var finishedAt: Instant? = null,

    @Column(nullable = false)
    var inserted: Int = 0,

    @Column(nullable = false)
    var updated: Int = 0,

    @Column(name = "soft_deleted", nullable = false)
    var softDeleted: Int = 0,

    @Column(nullable = false)
    var unchanged: Int = 0,

    @Column(name = "skipped_reason", length = 64)
    var skippedReason: String? = null,

    @Column(columnDefinition = "TEXT")
    var error: String? = null,

    @Column(name = "duration_ms", nullable = false)
    var durationMs: Long = 0
)
