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

/**
 * Tabla histórica: su contenido se fusionó en `net_diag_incident_event`. Ya no se escribe;
 * solo la drena el servicio de retención.
 */
@Entity
@Table(
    name = "net_diag_alert_decision",
    indexes = [
        Index(name = "idx_ndad_created", columnList = "created_at")
    ]
)
class NetDiagAlertDecision(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "target_id")
    var target: NetDiagTarget? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "incident_id")
    var incident: NetDiagIncident? = null,

    @Column(nullable = false, length = 64)
    var decision: String = "",

    @Column(nullable = false, length = 64)
    var reasonCode: String = "",

    @Column(columnDefinition = "TEXT")
    var details: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
