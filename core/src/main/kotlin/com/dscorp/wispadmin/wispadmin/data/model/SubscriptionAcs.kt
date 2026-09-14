package com.dscorp.wispadmin.wispadmin.data.model

import com.dscorp.wispadmin.wispadmin.dto.SubscriptionAcsDto
import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table

/**
 * Snapshot ACS (GenieACS) 1:1 con [Subscription].
 * Status operativo del alta sigue en subscription.tr069_*; esta tabla es proyección para plataforma.
 */
@Entity
@Table(
    name = "subscription_acs",
    indexes = [
        Index(name = "idx_subscription_acs_device_id", columnList = "genieacs_device_id"),
        Index(name = "idx_subscription_acs_last_inform", columnList = "last_inform_at"),
    ]
)
data class SubscriptionAcs(
    @Id
    @Column(name = "subscription_id")
    var subscriptionId: Int = 0,

    @Column(name = "genieacs_device_id", length = 128)
    var genieacsDeviceId: String? = null,

    @Column(name = "serial_suffix", length = 6)
    var serialSuffix: String? = null,

    @Column(name = "smartolt_serial", length = 64)
    var smartoltSerial: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "provision_status", length = 32)
    var provisionStatus: Tr069ProvisionStatus? = null,

    @Column(name = "last_error", length = 500)
    var lastError: String? = null,

    @Column(name = "provisioned_at")
    var provisionedAt: LocalDateTime? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "last_inform_at")
    var lastInformAt: LocalDateTime? = null,

    @Column(name = "product_class", length = 64)
    var productClass: String? = null,

    @Column(name = "oui", length = 16)
    var oui: String? = null,

    @Column(name = "manufacturer", length = 128)
    var manufacturer: String? = null,

    @Column(name = "connection_request_url", length = 512)
    var connectionRequestUrl: String? = null,

    @Column(name = "wan_ip_cache", length = 45)
    var wanIpCache: String? = null,

    @Column(name = "ssid_24", length = 32)
    var ssid24: String? = null,

    @Column(name = "ssid_5", length = 32)
    var ssid5: String? = null,

    @Column(name = "software_version", length = 64)
    var softwareVersion: String? = null,

    @Column(name = "hardware_version", length = 64)
    var hardwareVersion: String? = null,

    @Column(name = "last_boot_at")
    var lastBootAt: LocalDateTime? = null,

    @Column(name = "last_task_id", length = 64)
    var lastTaskId: String? = null,

    @Column(name = "last_task_status", length = 32)
    var lastTaskStatus: String? = null,

    @Column(name = "last_task_at")
    var lastTaskAt: LocalDateTime? = null,

    @Column(name = "lab", nullable = false)
    var lab: Boolean = false,
) {
    fun toDto() = SubscriptionAcsDto(
        subscriptionId = subscriptionId,
        genieacsDeviceId = genieacsDeviceId,
        serialSuffix = serialSuffix,
        smartoltSerial = smartoltSerial,
        provisionStatus = provisionStatus,
        tr069RequiresManualConfig = provisionStatus == Tr069ProvisionStatus.MANUAL_REQUIRED,
        lastError = lastError,
        provisionedAt = provisionedAt,
        updatedAt = updatedAt,
        lastInformAt = lastInformAt,
        productClass = productClass,
        oui = oui,
        manufacturer = manufacturer,
        connectionRequestUrl = connectionRequestUrl,
        wanIpCache = wanIpCache,
        ssid24 = ssid24,
        ssid5 = ssid5,
        softwareVersion = softwareVersion,
        hardwareVersion = hardwareVersion,
        lastBootAt = lastBootAt,
        lastTaskId = lastTaskId,
        lastTaskStatus = lastTaskStatus,
        lastTaskAt = lastTaskAt,
        lab = lab,
    )
}
