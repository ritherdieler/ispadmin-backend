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
@Table(name = "olt_mgr_olt")
class OltMgrOlt(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, length = 64)
    var name: String = "",

    @Column(name = "ip_address", nullable = false, length = 64)
    var ipAddress: String = "",

    @Column(name = "mgmt_protocol", nullable = false, length = 10)
    var mgmtProtocol: String = "ssh",

    @Column(name = "mgmt_port", nullable = false)
    var mgmtPort: Int = 22,

    @Column(name = "username_enc", nullable = false, columnDefinition = "TEXT")
    var usernameEnc: String = "",

    @Column(name = "password_enc", nullable = false, columnDefinition = "TEXT")
    var passwordEnc: String = "",

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "model_id")
    var model: OltMgrOltModel? = null,

    @Column(name = "hardware_version", length = 64)
    var hardwareVersion: String? = null,

    @Column(name = "software_version", length = 64)
    var softwareVersion: String? = null,

    @Column(name = "supported_pon_types", length = 64)
    var supportedPonTypes: String? = "GPON",

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
