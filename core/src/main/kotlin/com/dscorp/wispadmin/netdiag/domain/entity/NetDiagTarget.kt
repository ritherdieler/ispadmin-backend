package com.dscorp.wispadmin.netdiag.domain.entity

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "net_diag_target")
class NetDiagTarget(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, length = 128)
    var name: String = "",

    @Column(name = "device_ref_id", nullable = false)
    var deviceRefId: Long = 0,

    @Column(name = "parent_target_id")
    var parentTargetId: Long? = null,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(name = "poll_interval_ms", nullable = false)
    var pollIntervalMs: Long = 60000,

    @Column(name = "monitor_config", columnDefinition = "TEXT")
    var monitorConfig: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
