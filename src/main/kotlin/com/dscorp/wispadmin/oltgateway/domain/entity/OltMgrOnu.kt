package com.dscorp.wispadmin.oltgateway.domain.entity

import java.math.BigDecimal
import java.time.Instant
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.OneToOne
import javax.persistence.Table
import javax.persistence.UniqueConstraint

@Entity
@Table(
    name = "olt_mgr_onu",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["olt_id", "board", "port", "onu_index"])
    ]
)
class OltMgrOnu(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true, length = 32)
    var sn: String = "",

    @Column(name = "external_id", nullable = false, unique = true, length = 64)
    var externalId: String = "",

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "olt_id", nullable = false)
    var olt: OltMgrOlt,

    @Column(nullable = false)
    var board: Int = 0,

    @Column(nullable = false)
    var port: Int = 0,

    @Column(name = "onu_index", nullable = false)
    var onuIndex: Int = 0,

    @Column(name = "pon_type", nullable = false, length = 8)
    var ponType: String = "gpon",

    @Column(name = "gpon_channel", nullable = false, length = 8)
    var gponChannel: String = "gpon",

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "onu_type_id")
    var onuType: OltMgrOnuType? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "custom_template_id")
    var customTemplate: OltMgrCustomTemplate? = null,

    @Column(name = "line_profile_maptype", length = 8)
    var lineProfileMaptype: String? = "vlan",

    @Column(name = "imported_line_profile_id")
    var importedLineProfileId: Long? = null,

    @Column(name = "imported_service_profile_id")
    var importedServiceProfileId: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "zone_id")
    var zone: OltMgrZone? = null,

    @Column(name = "splitter_id")
    var splitterId: Long? = null,

    @Column(name = "splitter_port")
    var splitterPort: Int? = null,

    @Column(length = 512)
    var name: String? = null,

    @Column(length = 255)
    var address: String? = null,

    @Column(length = 128)
    var contact: String? = null,

    var latitude: BigDecimal? = null,

    var longitude: BigDecimal? = null,

    @Column(length = 12)
    var mode: String? = "routing",

    @Column(name = "wan_mode", length = 24)
    var wanMode: String? = "onu_webpage",

    @Column(name = "configuration_method", length = 8)
    var configurationMethod: String? = "omci",

    @Column(name = "main_vlan_id")
    var mainVlanId: Int? = null,

    @Column(name = "wan_ip_source", length = 8)
    var wanIpSource: String? = null,

    @Column(name = "ip_address", length = 64)
    var ipAddress: String? = null,

    @Column(name = "subnet_mask", length = 64)
    var subnetMask: String? = null,

    @Column(name = "default_gateway", length = 64)
    var defaultGateway: String? = null,

    @Column(length = 64)
    var dns1: String? = null,

    @Column(length = 64)
    var dns2: String? = null,

    @Column(name = "mgmt_ip_mode", length = 10)
    var mgmtIpMode: String? = "inactive",

    @Column(name = "mgmt_vlan_id")
    var mgmtVlanId: Int? = null,

    @Column(name = "mgmt_ip_address", length = 64)
    var mgmtIpAddress: String? = null,

    @Column(name = "administrative_status", nullable = false, length = 10)
    var administrativeStatus: String = "enabled",

    @Column(name = "emergency_stopped", nullable = false)
    var emergencyStopped: Boolean = false,

    @Column(name = "authorization_date")
    var authorizationDate: Instant? = null,

    @Column(name = "authorized_by_user_id")
    var authorizedByUserId: Long? = null,

    @Column(name = "imported_from_olt", nullable = false)
    var importedFromOlt: Boolean = false,

    @Column(name = "synced_after_import", nullable = false)
    var syncedAfterImport: Boolean = true,

    @Column(name = "last_resync_failed", nullable = false)
    var lastResyncFailed: Boolean = false,

    @Column(name = "last_resync_at")
    var lastResyncAt: Instant? = null,

    @Column(name = "line_profile_name", length = 64)
    var lineProfileName: String? = null,

    @Column(name = "service_profile_name", length = 64)
    var serviceProfileName: String? = null,

    @Column(name = "onu_type_name", length = 64)
    var onuTypeName: String? = null,

    @Column(name = "zone_name", length = 64)
    var zoneName: String? = null,

    @Column(name = "custom_profile", length = 64)
    var customProfile: String? = null,

    @OneToOne(
        mappedBy = "onu",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    var status: OltMgrOnuStatusCurrent? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
)
