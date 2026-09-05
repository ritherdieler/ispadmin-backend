package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.Tr069ProvisionStatus
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.springframework.stereotype.Service

@Service
class CpeProvisionFlagService(
    private val subscriptions: SubscriptionRepository,
) {
    fun apply(sn: String, cpeStatus: String) {
        val matches = subscriptions.findByExactOnuSerial(sn)
        if (matches.size != 1) return
        val subscription = matches.single()
        val mapped = when (cpeStatus.uppercase()) {
            "COMPLETE" -> Tr069ProvisionStatus.COMPLETE
            "PENDING" -> Tr069ProvisionStatus.PENDING
            "FAILED" -> Tr069ProvisionStatus.FAILED
            "NA" -> Tr069ProvisionStatus.NA
            else -> return
        }
        subscription.tr069ProvisionStatus = mapped
        subscriptions.save(subscription)
    }
}
