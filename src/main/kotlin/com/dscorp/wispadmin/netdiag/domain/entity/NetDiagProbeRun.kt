package com.dscorp.wispadmin.netdiag.domain.entity

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
@Table(name = "net_diag_probe_run")
class NetDiagProbeRun(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false)
    var target: NetDiagTarget,

    @Column(nullable = false, length = 32)
    var status: String = "PENDING",

    @Column(name = "started_at", nullable = false)
    var startedAt: Instant = Instant.now(),

    @Column(name = "finished_at")
    var finishedAt: Instant? = null,

    @Column(name = "latency_ms")
    var latencyMs: Long? = null,

    @Column(columnDefinition = "TEXT")
    var payload: String? = null,

    @Column(columnDefinition = "TEXT")
    var error: String? = null
)
