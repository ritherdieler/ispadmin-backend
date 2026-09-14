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
@Table(name = "olt_mgr_audit_log")
class OltMgrAuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "olt_id")
    var olt: OltMgrOlt? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "onu_id")
    var onu: OltMgrOnu? = null,

    @Column(nullable = false, length = 64)
    var action: String = "",

    @Column(name = "user_id")
    var userId: Long? = null,

    @Column(nullable = false, length = 8)
    var source: String = "api",

    @Column(name = "ip_address", length = 64)
    var ipAddress: String? = null,

    @Column(columnDefinition = "TEXT")
    var details: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
