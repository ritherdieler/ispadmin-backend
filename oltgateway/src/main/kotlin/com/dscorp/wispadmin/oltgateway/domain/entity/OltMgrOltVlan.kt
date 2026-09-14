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
    name = "olt_mgr_olt_vlan",
    uniqueConstraints = [UniqueConstraint(columnNames = ["olt_id", "vlan_id"])]
)
class OltMgrOltVlan(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "olt_id", nullable = false)
    var olt: OltMgrOlt,

    @Column(name = "vlan_id", nullable = false)
    var vlanId: Int = 0,

    @Column(nullable = false, length = 16)
    var purpose: String = "user",

    @Column(length = 128)
    var description: String? = null
)
