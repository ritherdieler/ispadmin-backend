package com.dscorp.wispadmin.oltgateway.domain.entity

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table
import javax.persistence.UniqueConstraint

@Entity
@Table(
    name = "olt_mgr_onu_autofind",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_omoa_sn", columnNames = ["sn"])
    ],
    indexes = [
        Index(name = "idx_omoa_last_seen", columnList = "last_seen_at")
    ]
)
class OltMgrOnuAutofind(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "sn", nullable = false, length = 32)
    var sn: String = "",

    @Column(name = "frame", nullable = false)
    var frame: Int = 0,

    @Column(name = "board", nullable = false)
    var board: Int = 0,

    @Column(name = "port", nullable = false)
    var port: Int = 0,

    @Column(name = "pon_type", nullable = false, length = 8)
    var ponType: String = "gpon",

    @Column(name = "vendor_id", length = 32)
    var vendorId: String? = null,

    @Column(name = "equipment_id", length = 64)
    var equipmentId: String? = null,

    @Column(name = "software_version", length = 64)
    var softwareVersion: String? = null,

    @Column(name = "autofind_time", length = 64)
    var autofindTime: String? = null,

    @Column(name = "source", nullable = false, length = 16)
    var source: String = "background",

    @Column(name = "first_seen_at", nullable = false)
    var firstSeenAt: Instant = Instant.now(),

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now()
)
