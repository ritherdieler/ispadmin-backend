package com.dscorp.wispadmin.servicehealth.client

import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider

class HealthOltGatewayHttpClientTest {

    @Test
    fun `degrada a null si olt gateway internal-base-url esta vacio`() {
        val subscriptions = mockk<ObjectProvider<SubscriptionRepository>>()
        every { subscriptions.ifAvailable } returns null
        val client = HealthOltGatewayHttpClient(baseUrl = "", apiKey = "k", subscriptions = subscriptions)
        assertNull(client.findBySn("HWTC1"))
        assertNull(client.findByExternalId("ext"))
        assertEquals(emptyList<Any>(), client.findByOlt(1L))
        assertNull(client.findOltIdByName("olt"))
        assertEquals(false, client.refreshSubscription(1).collected)
        assertEquals(emptyList<Any>(), client.pullOptical())
        assertEquals(emptyList<Any>(), client.pullStates())
    }
}
