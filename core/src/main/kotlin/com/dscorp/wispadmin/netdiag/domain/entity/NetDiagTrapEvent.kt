package com.dscorp.wispadmin.netdiag.domain.entity

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "net_diag_trap_event")
class NetDiagTrapEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "target_id")
    var targetId: Long? = null,

    @Column(name = "source_host", length = 128)
    var sourceHost: String? = null,

    @Column(name = "trap_type", length = 64)
    var trapType: String? = null,

    @Column(length = 256)
    var oid: String? = null,

    @Column(name = "reason_code", nullable = false, length = 64)
    var reasonCode: String = "",

    @Column(name = "component", length = 128)
    var component: String? = null,

    @Column(name = "var_binds", columnDefinition = "TEXT")
    var varBinds: String? = null,

    @Column(columnDefinition = "TEXT")
    var raw: String? = null,

    @Column(name = "received_at", nullable = false)
    var receivedAt: Instant = Instant.now()
)
