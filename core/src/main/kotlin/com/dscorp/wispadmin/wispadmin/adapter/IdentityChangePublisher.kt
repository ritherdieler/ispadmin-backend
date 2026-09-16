package com.dscorp.wispadmin.wispadmin.adapter

import com.dscorp.wispadmin.servicehealth.service.SubscriptionIdentityChanged
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Aspect
@Component
@ConditionalOnProperty(
    prefix = "gigafiber.subsystems.servicehealth",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class IdentityChangePublisher(
    private val publisher: ApplicationEventPublisher,
) {
    @AfterReturning(
        pointcut = "execution(* org.springframework.data.repository.CrudRepository+.save(..)) || execution(* org.springframework.data.jpa.repository.JpaRepository+.saveAndFlush(..)) || execution(* org.springframework.data.repository.CrudRepository+.saveAll(..))",
        returning = "result",
    )
    fun saved(result: Any?) {
        val values = if (result is Iterable<*>) result.toList() else listOf(result)
        values.forEach { value ->
            val id = when (value) {
                is Subscription -> value.id
                else -> null
            }
            if (id != null) publisher.publishEvent(SubscriptionIdentityChanged(id))
        }
    }
}
