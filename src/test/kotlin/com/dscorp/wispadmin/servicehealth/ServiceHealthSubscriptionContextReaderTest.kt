package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.service.ServiceHealthSubscriptionContextReader
import com.dscorp.wispadmin.wispadmin.repository.ServiceHealthSubscriptionView
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServiceHealthSubscriptionContextReaderTest {
    private val subscriptions = mockk<SubscriptionRepository>()
    private val reader = ServiceHealthSubscriptionContextReader(subscriptions)

    @Test
    fun `builds a normalized person name and service context`() {
        every { subscriptions.findServiceHealthContextById(2329) } returns view(
            firstName = "  Sergio  ", lastName = " Carrillo Diestra ", clientType = Subscription.ClientType.PERSON,
            serviceStatus = ServiceStatus.ACTIVE, planName = "Fibra 500 Mbps", ip = "10.0.0.5",
        )

        val context = reader.read(2329)

        assertEquals("Sergio Carrillo Diestra", context.subscriber.displayName)
        assertEquals("PERSON", context.subscriber.clientType)
        assertEquals("Fibra 500 Mbps", context.serviceContext.planName)
        assertEquals("ACTIVE", context.serviceContext.serviceStatus)
    }

    @Test
    fun `uses business name for company subscriptions`() {
        every { subscriptions.findServiceHealthContextById(7) } returns view(
            firstName = "Representante", lastName = "Legal", businessName = "  Gigafiber SAC  ", clientType = Subscription.ClientType.BUSINESS,
        )

        assertEquals("Gigafiber SAC", reader.read(7).subscriber.displayName)
    }

    @Test
    fun `falls back across available names`() {
        every { subscriptions.findServiceHealthContextById(8) } returns view(businessName = "Empresa disponible", clientType = Subscription.ClientType.PERSON)
        every { subscriptions.findServiceHealthContextById(9) } returns view(firstName = "Ana", lastName = "Paredes", clientType = Subscription.ClientType.BUSINESS)

        assertEquals("Empresa disponible", reader.read(8).subscriber.displayName)
        assertEquals("Ana Paredes", reader.read(9).subscriber.displayName)
    }

    @Test
    fun `uses an explicit fallback for a blank subscriber`() {
        every { subscriptions.findServiceHealthContextById(10) } returns view(clientType = Subscription.ClientType.PERSON)

        assertEquals("Cliente sin nombre registrado", reader.read(10).subscriber.displayName)
    }

    private fun view(
        firstName: String? = null,
        lastName: String? = null,
        businessName: String? = null,
        clientType: Subscription.ClientType = Subscription.ClientType.PERSON,
        serviceStatus: ServiceStatus = ServiceStatus.ACTIVE,
        planName: String? = null,
        ip: String? = null,
    ) = object : ServiceHealthSubscriptionView {
        override fun getFirstName() = firstName
        override fun getLastName() = lastName
        override fun getBusinessName() = businessName
        override fun getClientType() = clientType
        override fun getServiceStatus() = serviceStatus
        override fun getPlanName() = planName
        override fun getIp() = ip
    }
}
