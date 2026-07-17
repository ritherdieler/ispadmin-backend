package com.dscorp.wispadmin.oltgateway.domain.entity

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "olt_mgr_olt_model")
class OltMgrOltModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 32)
    var code: String = "",

    @Column(nullable = false, length = 32)
    var vendor: String = "",

    @Column(nullable = false, length = 32)
    var product: String = "",

    @Column(nullable = false, length = 32)
    var family: String = "",

    @Column(name = "max_concurrent_cli_sessions", nullable = false)
    var maxConcurrentCliSessions: Int = 1,

    @Column(name = "max_slot_probe", nullable = false)
    var maxSlotProbe: Int = 7,

    @Column(name = "default_ports_per_gpon_board", nullable = false)
    var defaultPortsPerGponBoard: Int = 16,

    @Column(columnDefinition = "TEXT")
    var notes: String? = null
)
