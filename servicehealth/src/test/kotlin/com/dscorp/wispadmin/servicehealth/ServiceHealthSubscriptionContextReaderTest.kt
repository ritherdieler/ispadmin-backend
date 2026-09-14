package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.servicehealth.port.SubscriptionDirectoryPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionHealthContext
import com.dscorp.wispadmin.servicehealth.service.ServiceHealthSubscriptionContextReader
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServiceHealthSubscriptionContextReaderTest {
    private val directory = mockk<SubscriptionDirectoryPort>()
    private val reader = ServiceHealthSubscriptionContextReader(directory)

    @Test
    fun `builds a normalized person name and service context`() {
        every { directory.findContext(2329) } returns view(
            firstName = "  Sergio  ",
            lastName = " Carrillo Diestra ",
            clientType = "PERSON",
            serviceStatus = "ACTIVE",
            planName = "Fibra 500 Mbps",
            ip = "10.0.0.5",
        )

        val context = reader.read(2329)

        assertEquals("Sergio Carrillo Diestra", context.subscriber.displayName)
        assertEquals("PERSON", context.subscriber.clientType)
        assertEquals("Fibra 500 Mbps", context.serviceContext.planName)
        assertEquals("ACTIVE", context.serviceContext.serviceStatus)
    }

    @Test
    fun `uses business name for company subscriptions`() {
        every { directory.findContext(7) } returns view(
            firstName = "Representante",
            lastName = "Legal",
            businessName = "  Gigafiber SAC  ",
            clientType = "BUSINESS",
        )

        assertEquals("Gigafiber SAC", reader.read(7).subscriber.displayName)
    }

    @Test
    fun `falls back across available names`() {
        every { directory.findContext(8) } returns view(businessName = "Empresa disponible", clientType = "PERSON")
        every { directory.findContext(9) } returns view(firstName = "Ana", lastName = "Paredes", clientType = "BUSINESS")

        assertEquals("Empresa disponible", reader.read(8).subscriber.displayName)
        assertEquals("Ana Paredes", reader.read(9).subscriber.displayName)
    }

    @Test
    fun `uses an explicit fallback for a blank subscriber`() {
        every { directory.findContext(10) } returns view(clientType = "PERSON")

        assertEquals("Cliente sin nombre registrado", reader.read(10).subscriber.displayName)
    }

    private fun view(
        firstName: String? = null,
        lastName: String? = null,
        businessName: String? = null,
        clientType: String = "PERSON",
        serviceStatus: String = "ACTIVE",
        planName: String? = null,
        ip: String? = null,
    ) = SubscriptionHealthContext(
        firstName = firstName,
        lastName = lastName,
        businessName = businessName,
        clientType = clientType,
        serviceStatus = serviceStatus,
        planName = planName,
        ip = ip,
    )
}
