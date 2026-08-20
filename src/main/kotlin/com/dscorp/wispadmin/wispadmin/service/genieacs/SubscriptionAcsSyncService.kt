package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.dscorp.wispadmin.wispadmin.data.model.SubscriptionAcs
import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionAcsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Persistencia del snapshot ACS tras provisión TR-069.
 * No altera tr069_* de subscription; solo upsert 1:1 en subscription_acs.
 */
@Service
class SubscriptionAcsSyncService(
    private val repository: SubscriptionAcsRepository,
) {
    private val log = LoggerFactory.getLogger(SubscriptionAcsSyncService::class.java)

    @Transactional
    fun upsertFromProvision(
        subscriptionId: Int,
        outcome: Tr069ProvisionOutcome,
        smartoltSerial: String?,
    ) {
        // COMPLETE o MANUAL_REQUIRED solo si ya hay deviceId (match parcial / total).
        if (outcome.status == Tr069ProvisionStatus.NA) return
        if (outcome.deviceId.isNullOrBlank()) return

        try {
            val now = LocalDateTime.now()
            val snap = outcome.acsSnapshot
            val existing = repository.findById(subscriptionId).orElse(null)
            val row = existing ?: SubscriptionAcs(subscriptionId = subscriptionId)

            row.genieacsDeviceId = outcome.deviceId ?: row.genieacsDeviceId
            row.serialSuffix = snap?.serialSuffix ?: row.serialSuffix
            row.smartoltSerial = smartoltSerial?.takeIf { it.isNotBlank() } ?: row.smartoltSerial
            row.provisionStatus = outcome.status
            row.lastError = when (outcome.status) {
                Tr069ProvisionStatus.COMPLETE, Tr069ProvisionStatus.NA -> null
                else -> (outcome.error ?: outcome.message)?.take(500)
            }
            if (outcome.status == Tr069ProvisionStatus.COMPLETE) {
                row.provisionedAt = row.provisionedAt ?: now
            }
            row.updatedAt = now
            row.lastInformAt = snap?.lastInformAt ?: row.lastInformAt
            row.productClass = snap?.productClass ?: row.productClass
            row.oui = snap?.oui ?: row.oui
            row.manufacturer = snap?.manufacturer ?: row.manufacturer
            row.connectionRequestUrl = snap?.connectionRequestUrl ?: row.connectionRequestUrl
            row.wanIpCache = snap?.wanIpCache ?: row.wanIpCache
            row.ssid24 = snap?.ssid24 ?: row.ssid24
            row.ssid5 = snap?.ssid5 ?: row.ssid5
            row.softwareVersion = snap?.softwareVersion ?: row.softwareVersion
            row.hardwareVersion = snap?.hardwareVersion ?: row.hardwareVersion
            row.lastBootAt = snap?.lastBootAt ?: row.lastBootAt
            row.lastTaskId = snap?.lastTaskId ?: row.lastTaskId
            row.lastTaskStatus = snap?.lastTaskStatus ?: row.lastTaskStatus
            row.lastTaskAt = snap?.lastTaskAt ?: row.lastTaskAt

            repository.save(row)
        } catch (ex: Exception) {
            log.warn(
                "No se pudo upsert subscription_acs para suscripción {}: {}",
                subscriptionId,
                ex.message,
            )
        }
    }
}
