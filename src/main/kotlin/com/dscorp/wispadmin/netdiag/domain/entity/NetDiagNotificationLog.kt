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
@Table(name = "net_diag_notification_log")
class NetDiagNotificationLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "incident_id")
    var incident: NetDiagIncident? = null,

    @Column(nullable = false, length = 32)
    var channel: String = "whatsapp",

    @Column(nullable = false, length = 32)
    var status: String = "PENDING",

    @Column(name = "destination", length = 64)
    var destination: String? = null,

    @Column(columnDefinition = "TEXT")
    var payload: String? = null,

    @Column(columnDefinition = "TEXT")
    var error: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
