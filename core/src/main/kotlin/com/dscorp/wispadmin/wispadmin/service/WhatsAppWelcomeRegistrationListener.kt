package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.service.subscription.SubscriptionRegisteredEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class WhatsAppWelcomeRegistrationListener(
    private val welcomeRegistrationService: WhatsAppWelcomeRegistrationService
) {

    private val log = LoggerFactory.getLogger(WhatsAppWelcomeRegistrationListener::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onSubscriptionRegistered(event: SubscriptionRegisteredEvent) {
        try {
            welcomeRegistrationService.sendWelcomeIfApplicable(event.subscriptionId)
        } catch (e: Exception) {
            log.error(
                "Error inesperado al procesar bienvenida WhatsApp para suscripcion {}: {}",
                event.subscriptionId,
                e.message,
                e
            )
        }
    }
}
