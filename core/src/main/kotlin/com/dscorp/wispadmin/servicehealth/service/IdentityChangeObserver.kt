package com.dscorp.wispadmin.servicehealth.service

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

data class SubscriptionIdentityChanged(val id: Int)

@Component
class IdentityChangeObserver(private val identity: IdentityService) {
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT,fallbackExecution=true)
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    fun changed(event: SubscriptionIdentityChanged) {
        identity.reconcile(event.id)
    }
}
