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
    name = "olt_mgr_onu_service_port",
    uniqueConstraints = [UniqueConstraint(columnNames = ["onu_id", "user_vlan_id"])]
)
class OltMgrOnuServicePort(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "onu_id", nullable = false)
    var onu: OltMgrOnu,

    @Column(name = "olt_service_port_id")
    var oltServicePortId: Int? = null,

    @Column(name = "user_vlan_id", nullable = false)
    var userVlanId: Int = 0,

    @Column(name = "cvlan_id")
    var cvlanId: Int? = null,

    @Column(name = "svlan_id")
    var svlanId: Int? = null,

    @Column(name = "tag_transform", nullable = false, length = 20)
    var tagTransform: String = "translate",

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "download_speed_profile_id")
    var downloadSpeed: OltMgrSpeedProfile? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "upload_speed_profile_id")
    var uploadSpeed: OltMgrSpeedProfile? = null
)
