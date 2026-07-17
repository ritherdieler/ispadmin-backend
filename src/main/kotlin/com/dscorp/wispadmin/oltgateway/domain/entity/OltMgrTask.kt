package com.dscorp.wispadmin.oltgateway.domain.entity

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "olt_mgr_task")
class OltMgrTask(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "olt_id")
    var olt: OltMgrOlt? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "onu_id")
    var onu: OltMgrOnu? = null,

    @Column(nullable = false, length = 24)
    var type: String = "",

    @Column(columnDefinition = "TEXT")
    var payload: String? = null,

    @Column(nullable = false, length = 10)
    var status: String = "queued",

    @Column(name = "requested_by_user_id")
    var requestedByUserId: Long? = null,

    @Column(nullable = false, length = 8)
    var source: String = "api",

    @Column(columnDefinition = "TEXT")
    var error: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "finished_at")
    var finishedAt: Instant? = null
)
