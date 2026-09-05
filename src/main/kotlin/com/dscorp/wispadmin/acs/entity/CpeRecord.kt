package com.dscorp.wispadmin.acs.entity

import com.dscorp.wispadmin.acs.CpeStatus
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "cpe_record")
class CpeRecord(
    @Id
    @Column(name = "sn", length = 64)
    var sn: String = "",

    @Column(name = "unique_external_id", length = 128)
    var uniqueExternalId: String? = null,

    @Column(name = "device_id", length = 128)
    var deviceId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    var status: CpeStatus = CpeStatus.PENDING,

    @Column(name = "message", length = 500)
    var message: String? = null,

    @Column(name = "product_class", length = 64)
    var productClass: String? = null,

    @Column(name = "wan_ip", length = 45)
    var wanIp: String? = null,

    @Column(name = "ssid_24", length = 32)
    var ssid24: String? = null,

    @Column(name = "ssid_5", length = 32)
    var ssid5: String? = null,

    @Column(name = "software_version", length = 64)
    var softwareVersion: String? = null,

    @Column(name = "last_inform_at")
    var lastInformAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
