package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.service.subscription.SubscriptionRegisteredEvent
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class WhatsAppWelcomeRegistrationListenerTest {

    private val welcomeRegistrationService = mock(WhatsAppWelcomeRegistrationService::class.java)
    private val listener = WhatsAppWelcomeRegistrationListener(welcomeRegistrationService)

    @Test
    fun `sends welcome once when subscription registration completes`() {
        listener.onSubscriptionRegistered(SubscriptionRegisteredEvent(subscriptionId = 99))

        verify(welcomeRegistrationService, times(1)).sendWelcomeIfApplicable(99)
        verifyNoMoreInteractions(welcomeRegistrationService)
    }
}
