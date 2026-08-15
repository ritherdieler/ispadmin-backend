package com.dscorp.wispadmin.wispadmin.scheduled

import com.dscorp.wispadmin.wispadmin.service.SubscriptionProvisionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SubscriptionProvisionReconciliationScheduler(
    private val subscriptionProvisionService: SubscriptionProvisionService
) {
    private val logger = LoggerFactory.getLogger(SubscriptionProvisionReconciliationScheduler::class.java)

    @Scheduled(
        fixedDelayString = "\${subscription.provision.reconciliation.interval-ms:300000}",
        initialDelayString = "\${subscription.provision.reconciliation.initial-delay-ms:60000}"
    )
    fun reconcile() {
        val count = subscriptionProvisionService.reconcileDue()
        if (count > 0) {
            logger.info("Reconciliacion de provision MikroTik/OLT procesada: $count suscripciones")
        }
    }
}
