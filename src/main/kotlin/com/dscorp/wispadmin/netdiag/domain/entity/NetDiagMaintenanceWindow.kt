package com.dscorp.wispadmin.netdiag.domain.entity

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "net_diag_maintenance_window")
class NetDiagMaintenanceWindow(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "target_id")
    var targetId: Long? = null,

    @Column(nullable = false, length = 255)
    var title: String = "",

    @Column(length = 512)
    var description: String? = null,

    @Column(name = "starts_at", nullable = false)
    var startsAt: Instant = Instant.now(),

    @Column(name = "ends_at", nullable = false)
    var endsAt: Instant = Instant.now(),

    @Column(name = "suppress_notifications", nullable = false)
    var suppressNotifications: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
