package com.dscorp.wispadmin.oltgateway.domain.entity

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "olt_mgr_onu_type")
class OltMgrOnuType(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 64)
    var name: String = "",

    @Column(name = "pon_type", nullable = false, length = 8)
    var ponType: String = "gpon",

    @Column(nullable = false, length = 8)
    var channels: String = "G",

    @Column(name = "ethernet_ports", nullable = false)
    var ethernetPorts: Int = 1,

    @Column(name = "wifi_ports", nullable = false)
    var wifiPorts: Int = 0,

    @Column(name = "voip_ports", nullable = false)
    var voipPorts: Int = 0,

    @Column(name = "catv_ports", nullable = false)
    var catvPorts: Int = 0,

    @Column(name = "allow_custom_profiles", nullable = false)
    var allowCustomProfiles: Boolean = true,

    @Column(nullable = false, length = 20)
    var capability: String = "bridging_routing"
)
