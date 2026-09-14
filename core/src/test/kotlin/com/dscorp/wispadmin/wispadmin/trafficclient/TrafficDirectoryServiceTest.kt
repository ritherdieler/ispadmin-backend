package com.dscorp.wispadmin.wispadmin.trafficclient

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrafficDirectoryServiceTest {

    private val repository = mockk<SubscriptionRepository>()
    private val service = TrafficDirectoryService(repository)

    @Test
    fun `lista id ip y plan desde suscripciones de poll`() {
        val subscription = Subscription(
            id = 2360,
            firstName = "EEEFIBER",
            lastName = "PRUEBA",
            ip = "192.168.250.20",
            equipmentCondition = EquipmentCondition.LOAN,
        )
        subscription.hostDevice = NetworkDevice(id = 8, name = "CCR2", ipAddress = "38.224.231.4")
        subscription.plan = Plan(id = 1, name = "Fibra 100", downloadSpeed = 100, uploadSpeed = 50)
        every { repository.findForTrafficPolling() } returns listOf(subscription)

        val targets = service.list()

        assertEquals(1, targets.size)
        assertEquals(2360, targets.single().subscriptionId)
        assertEquals("192.168.250.20", targets.single().ip)
        assertEquals(8, targets.single().routerHint)
        assertEquals(100, targets.single().planDownloadMbps)
        assertEquals("EEEFIBER PRUEBA", targets.single().displayName)
    }
}
