package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.transaction.event.TransactionPhase

data class SubscriptionIdentityChanged(val id: Int)

/** Observe existing persistence boundaries without replacing provisioning or its ownership. */
@Aspect
@Component
class IdentityChangePublisher(private val publisher: ApplicationEventPublisher,private val properties: ServiceHealthProperties) {
    @AfterReturning(pointcut="execution(* org.springframework.data.repository.CrudRepository+.save(..)) || execution(* org.springframework.data.jpa.repository.JpaRepository+.saveAndFlush(..)) || execution(* org.springframework.data.repository.CrudRepository+.saveAll(..))",returning="result")
    fun saved(result: Any?) {
        val values=if(result is Iterable<*>) result.toList() else listOf(result)
        values.forEach { value ->
            val id=when(value) { is Subscription -> value.id; else -> null }
            if(properties.collects(id)) publisher.publishEvent(SubscriptionIdentityChanged(id!!))
        }
    }
}

@Component
class IdentityChangeObserver(private val subscriptions: SubscriptionRepository,private val identity: IdentityService) {
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT,fallbackExecution=true)
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    fun changed(event: SubscriptionIdentityChanged) {
        subscriptions.findById(event.id).orElse(null)?.let { identity.reconcile(it) }
    }
}
