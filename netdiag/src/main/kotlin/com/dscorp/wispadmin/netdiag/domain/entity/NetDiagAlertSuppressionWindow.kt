package com.dscorp.wispadmin.netdiag.domain.entity

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table
import javax.persistence.UniqueConstraint

/**
 * Contador agregado de señales suprimidas por correlación padre-hijo. Sustituye la fila por evento
 * que antes se guardaba en `net_diag_alert_decision` con decisión SUPPRESSED.
 */
@Entity
@Table(
    name = "net_diag_alert_suppression",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_ndas_window",
            columnNames = ["incident_id", "target_id", "reason_code", "window_start"]
        )
    ],
    indexes = [
        Index(name = "idx_ndas_window_start", columnList = "window_start")
    ]
)
class NetDiagAlertSuppressionWindow(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "incident_id", nullable = false)
    var incidentId: Long = 0,

    @Column(name = "target_id", nullable = false)
    var targetId: Long = 0,

    @Column(name = "reason_code", nullable = false, length = 64)
    var reasonCode: String = "",

    @Column(name = "window_start", nullable = false)
    var windowStart: Instant = Instant.EPOCH,

    @Column(name = "event_count", nullable = false)
    var eventCount: Long = 0,

    @Column(name = "first_seen_at", nullable = false)
    var firstSeenAt: Instant = Instant.now(),

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now()
)
