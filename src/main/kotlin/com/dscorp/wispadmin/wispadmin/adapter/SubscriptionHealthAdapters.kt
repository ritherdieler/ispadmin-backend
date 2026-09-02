package com.dscorp.wispadmin.wispadmin.adapter

import com.dscorp.wispadmin.servicehealth.port.AcsRegistryEntry
import com.dscorp.wispadmin.servicehealth.port.AcsSubscriptionPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionDirectoryPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionHealthRef
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionAcs
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionAcsRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class AcsSubscriptionAdapter(
    private val acs: SubscriptionAcsRepository
) : AcsSubscriptionPort {

    override fun find(subscriptionId: Int): AcsRegistryEntry? =
        acs.findById(subscriptionId).orElse(null)?.toEntry()

    override fun findDeviceId(subscriptionId: Int): String? =
        acs.findById(subscriptionId).orElse(null)?.genieacsDeviceId?.takeIf { it.isNotBlank() }

    override fun findSubscriptionIdsByDeviceId(deviceId: String): List<Int> =
        acs.findByGenieacsDeviceId(deviceId).map { it.subscriptionId }

    override fun labSubscriptionIds(): List<Int> = acs.findByLabIsTrue().map { it.subscriptionId }

    override fun isLab(subscriptionId: Int?): Boolean =
        subscriptionId != null && acs.findById(subscriptionId).orElse(null)?.lab == true

    override fun recordInform(
        subscriptionId: Int,
        lastInformAt: LocalDateTime,
        productClass: String,
        softwareVersion: String,
        updatedAt: LocalDateTime
    ) {
        val entry = acs.findById(subscriptionId).orElse(null) ?: return
        entry.lastInformAt = lastInformAt
        entry.productClass = productClass
        entry.softwareVersion = softwareVersion
        entry.updatedAt = updatedAt
        acs.save(entry)
    }

    private fun SubscriptionAcs.toEntry() = AcsRegistryEntry(
        subscriptionId = subscriptionId,
        deviceId = genieacsDeviceId?.takeIf { it.isNotBlank() },
        lastInformAt = lastInformAt,
        productClass = productClass,
        manufacturer = manufacturer,
        softwareVersion = softwareVersion,
        lab = lab
    )
}

@Component
class SubscriptionDirectoryAdapter(
    private val subscriptions: SubscriptionRepository
) : SubscriptionDirectoryPort {

    override fun allIds(): List<Int> = subscriptions.findAllIds()

    override fun exists(subscriptionId: Int): Boolean = subscriptions.existsById(subscriptionId)

    override fun find(subscriptionId: Int): SubscriptionHealthRef? =
        subscriptions.findById(subscriptionId).orElse(null)?.toRef()

    override fun lockIdentityOwner(subscriptionId: Int): SubscriptionHealthRef? =
        subscriptions.lockIdentityOwner(subscriptionId)?.toRef()

    override fun findIdsByOnuSerial(sn: String): List<Int> =
        subscriptions.findByExactOnuSerial(sn).mapNotNull { it.id }

    override fun findIdsByOnuSerialOrSuffix(sn: String, suffix: String): List<Int> =
        subscriptions.findByOnuSerialOrSuffix(sn, suffix).mapNotNull { it.id }

    private fun Subscription.toRef(): SubscriptionHealthRef? {
        val subscriptionId = id ?: return null
        return SubscriptionHealthRef(
            id = subscriptionId,
            onuSn = fiberOnuSn?.takeIf { it.isNotBlank() },
            ip = ip,
            vlan = vlan,
            hostDeviceId = hostDevice?.id,
            planId = plan?.id,
            planDownloadMbps = plan?.downloadSpeed,
            planUploadMbps = plan?.uploadSpeed,
            napBoxId = napBox?.id,
            serviceStatus = serviceStatus.name
        )
    }
}
