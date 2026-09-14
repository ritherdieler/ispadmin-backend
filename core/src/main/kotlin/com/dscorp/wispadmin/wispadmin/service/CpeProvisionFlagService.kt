package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.springframework.stereotype.Service

@Service
class CpeProvisionFlagService(
    private val subscriptions: SubscriptionRepository,
) {
    @org.springframework.transaction.annotation.Transactional
    fun apply(sn: String, cpeStatus: String, occurredAt: java.time.Instant? = null, eventId: String? = null) {
        val matches = subscriptions.findByExactOnuSerial(sn)
        if (matches.size != 1) return
        val candidate = matches.single()
        val subscription = if (occurredAt == null) candidate else subscriptions.lockIdentityOwner(requireNotNull(candidate.id)) ?: return
        if (occurredAt != null && (subscription.tr069StateEventId == eventId && eventId != null || subscription.tr069StateObservedAt?.let { !occurredAt.isAfter(it) } == true)) return
        val mapped = when (cpeStatus.uppercase()) {
            "COMPLETE" -> Tr069ProvisionStatus.COMPLETE
            "PENDING" -> Tr069ProvisionStatus.PENDING
            "FAILED" -> Tr069ProvisionStatus.FAILED
            "NA" -> Tr069ProvisionStatus.NA
            else -> return
        }
        subscription.tr069ProvisionStatus = mapped
        if (occurredAt != null) {
            subscription.tr069StateObservedAt = occurredAt
            subscription.tr069StateEventId = eventId
        }
        subscriptions.save(subscription)
    }
}
