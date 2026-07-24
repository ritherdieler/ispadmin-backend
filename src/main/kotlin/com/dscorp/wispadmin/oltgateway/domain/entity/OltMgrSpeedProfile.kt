package com.dscorp.wispadmin.oltgateway.domain.entity

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.UniqueConstraint

@Entity
@Table(
    name = "olt_mgr_speed_profile",
    uniqueConstraints = [UniqueConstraint(columnNames = ["name", "direction"])]
)
class OltMgrSpeedProfile(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, length = 32)
    var name: String = "",

    @Column(nullable = false, length = 8)
    var direction: String = "download",

    @Column(name = "speed_kbps", nullable = false)
    var speedKbps: Long = 0,

    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false,

    @Column(name = "use_prefix_suffix", nullable = false)
    var usePrefixSuffix: Boolean = false
)
