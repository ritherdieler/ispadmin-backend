package com.dscorp.wispadmin.oltgateway.domain.entity

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.persistence.UniqueConstraint

@Entity
@Table(
    name = "olt_mgr_olt_pon_port",
    uniqueConstraints = [UniqueConstraint(columnNames = ["olt_id", "board", "port"])]
)
class OltMgrOltPonPort(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "olt_id", nullable = false)
    var olt: OltMgrOlt,

    @Column(nullable = false)
    var board: Int = 0,

    @Column(nullable = false)
    var port: Int = 0,

    @Column(name = "pon_type", nullable = false, length = 8)
    var ponType: String = "gpon",

    @Column(name = "default_vlan_id")
    var defaultVlanId: Int? = null,

    @Column(name = "admin_enabled", nullable = false)
    var adminEnabled: Boolean = true,

    @Column(length = 128)
    var description: String? = null,

    @Column(name = "max_onu_id")
    var maxOnuId: Int? = 127
)
