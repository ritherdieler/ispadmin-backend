package com.dscorp.wispadmin.netdiag.domain.entity

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(
    name = "net_diag_incident",
    indexes = [
        Index(name = "idx_net_diag_incident_dedup_status", columnList = "dedup_key, status"),
        Index(name = "idx_net_diag_incident_status_opened_at", columnList = "status, opened_at"),
        Index(name = "idx_net_diag_incident_target_status", columnList = "target_id, status")
    ]
)
class NetDiagIncident(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "target_id")
    var target: NetDiagTarget? = null,

    @Column(name = "dedup_key", nullable = false, length = 255)
    var dedupKey: String = "",

    @Column(nullable = false, length = 32)
    var status: String = "OPEN",

    @Column(nullable = false, length = 8)
    var severity: String = "P2",

    @Column(nullable = false, length = 255)
    var title: String = "",

    @Column(name = "reason_code", length = 64)
    var reasonCode: String? = null,

    @Column(name = "opened_at", nullable = false)
    var openedAt: Instant = Instant.now(),

    @Column(name = "acknowledged_at")
    var acknowledgedAt: Instant? = null,

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,

    @Column(name = "last_notified_at")
    var lastNotifiedAt: Instant? = null,

    @Column(name = "silenced_until")
    var silencedUntil: Instant? = null
)
