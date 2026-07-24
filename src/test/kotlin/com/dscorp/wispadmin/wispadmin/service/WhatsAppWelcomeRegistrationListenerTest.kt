package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.service.subscription.SubscriptionRegisteredEvent
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class WhatsAppWelcomeRegistrationListenerTest {

    private val welcomeRegistrationService = mock(WhatsAppWelcomeRegistrationService::class.java)
    private val listener = WhatsAppWelcomeRegistrationListener(welcomeRegistrationService)

    @Test
    fun `delegates to welcome service after subscription registered event`() {
        listener.onSubscriptionRegistered(SubscriptionRegisteredEvent(subscriptionId = 99))

        verify(welcomeRegistrationService).sendWelcomeIfApplicable(99)
    }
}
