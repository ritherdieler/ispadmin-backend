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
    name = "net_diag_incident_event",
    indexes = [
        Index(name = "idx_ndie_created", columnList = "created_at"),
        Index(name = "idx_ndie_incident_created", columnList = "incident_id,created_at")
    ]
)
class NetDiagIncidentEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    var incident: NetDiagIncident,

    @Column(nullable = false, length = 64)
    var type: String = "",

    @Column(columnDefinition = "TEXT")
    var payload: String? = null,

    @Column(name = "reason_code", length = 64)
    var reasonCode: String? = null,

    @Column(name = "target_id")
    var targetId: Long? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
