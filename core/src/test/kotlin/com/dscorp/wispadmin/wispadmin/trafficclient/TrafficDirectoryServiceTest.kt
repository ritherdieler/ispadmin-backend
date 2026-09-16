package com.dscorp.wispadmin.wispadmin.trafficclient

import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrafficDirectoryServiceTest {

    private val repository = mockk<SubscriptionRepository>()
    private val service = TrafficDirectoryService(repository)

    @Test
    fun `lista id ip y plan desde suscripciones de poll`() {
        val subscription = subscription(
            id = 2360,
            firstName = "EEEFIBER",
            lastName = "PRUEBA",
            ip = "192.168.250.20",
            accessMode = AccessMode.STATIC_IP,
            installationType = InstallationType.FIBER,
        )
        every { repository.findForTrafficPolling() } returns listOf(subscription)

        val targets = service.list()

        assertEquals(1, targets.size)
        assertEquals(2360, targets.single().subscriptionId)
        assertEquals("192.168.250.20", targets.single().ip)
        assertNull(targets.single().pppoeUsername)
        assertEquals(8, targets.single().routerHint)
        assertEquals(100, targets.single().planDownloadMbps)
        assertEquals("EEEFIBER PRUEBA", targets.single().displayName)
    }

    @Test
    fun `FIBER PPPOE_DYNAMIC emite solo username aunque quede una IP`() {
        val subscription = subscription(
            id = 6,
            ip = "10.64.3.7",
            pppoeUsername = "gf6",
            accessMode = AccessMode.PPPOE_DYNAMIC,
            installationType = InstallationType.FIBER,
        )
        every { repository.findForTrafficPolling() } returns listOf(subscription)

        val target = service.list().single()

        assertEquals("", target.ip)
        assertEquals("gf6", target.pppoeUsername)
    }

    @Test
    fun `FIBER STATIC_IP emite solo IP aunque quede un username residual`() {
        val subscription = subscription(
            id = 100,
            ip = "192.168.250.20",
            pppoeUsername = "gf100",
            accessMode = AccessMode.STATIC_IP,
            installationType = InstallationType.FIBER,
        )
        every { repository.findForTrafficPolling() } returns listOf(subscription)

        val target = service.list().single()

        assertEquals("192.168.250.20", target.ip)
        assertNull(target.pppoeUsername)
    }

    @Test
    fun `WIRELESS STATIC_IP emite solo IP`() {
        val subscription = subscription(
            id = 840,
            ip = "192.168.22.170",
            accessMode = AccessMode.STATIC_IP,
            installationType = InstallationType.WIRELESS,
        )
        every { repository.findForTrafficPolling() } returns listOf(subscription)

        val target = service.list().single()

        assertEquals("192.168.22.170", target.ip)
        assertNull(target.pppoeUsername)
    }

    @Test
    fun `PPPOE_FIXED emite solo IP porque usa cola simple`() {
        val subscription = subscription(
            id = 50,
            ip = "192.168.26.40",
            pppoeUsername = "ANTONYCHEROLUBIO",
            accessMode = AccessMode.PPPOE_FIXED,
        )
        every { repository.findForTrafficPolling() } returns listOf(subscription)

        val target = service.list().single()

        assertEquals("192.168.26.40", target.ip)
        assertNull(target.pppoeUsername)
    }

    @Test
    fun `STATIC_IP sin IP no entra al directorio aunque tenga username`() {
        val subscription = subscription(
            id = 9,
            ip = null,
            pppoeUsername = "gf9",
            accessMode = AccessMode.STATIC_IP,
        )
        every { repository.findForTrafficPolling() } returns listOf(subscription)

        assertTrue(service.list().isEmpty())
    }

    @Test
    fun `PPPOE_DYNAMIC sin username no entra al directorio aunque tenga IP`() {
        val subscription = subscription(
            id = 11,
            ip = "10.64.0.2",
            pppoeUsername = null,
            accessMode = AccessMode.PPPOE_DYNAMIC,
            installationType = InstallationType.FIBER,
        )
        every { repository.findForTrafficPolling() } returns listOf(subscription)

        assertTrue(service.list().isEmpty())
    }

    private fun subscription(
        id: Int,
        ip: String? = null,
        pppoeUsername: String? = null,
        accessMode: AccessMode,
        installationType: InstallationType? = null,
        firstName: String? = "Lab",
        lastName: String? = "User",
    ): Subscription {
        val row = Subscription(
            id = id,
            firstName = firstName,
            lastName = lastName,
            ip = ip,
            equipmentCondition = EquipmentCondition.LOAN,
        )
        row.accessMode = accessMode
        row.installationType = installationType
        row.pppoeUsername = pppoeUsername
        row.hostDevice = NetworkDevice(id = 8, name = "CCR2", ipAddress = "38.224.231.4")
        row.plan = Plan(id = 1, name = "Fibra 100", downloadSpeed = 100, uploadSpeed = 50)
        return row
    }
}
