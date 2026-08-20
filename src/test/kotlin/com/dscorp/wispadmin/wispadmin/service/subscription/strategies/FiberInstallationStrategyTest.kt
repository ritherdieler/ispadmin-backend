package com.dscorp.wispadmin.wispadmin.service.subscription.strategies

import com.dscorp.wispadmin.routeros.port.MikrotikCommandException
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.InstallationType
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Place
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.dto.OnuDto
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.requestbody.SubscriptionRequest
import com.dscorp.wispadmin.wispadmin.service.CancelledOnuReuseService
import com.dscorp.wispadmin.wispadmin.data.model.GeoLocation
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.Runs
import io.mockk.slot
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class FiberInstallationStrategyTest {

    private lateinit var strategy: FiberInstallationStrategy

    @BeforeEach
    fun setUp() {
        strategy = FiberInstallationStrategy(mock(CancelledOnuReuseService::class.java))
    }

    @Test
    fun `resolveVlan devuelve 100 desde subscription vlan`() {
        val subscription = subscriptionWithHostDevice(cloudCoreRouter(id = 8, vlanId = 100)).apply {
            vlan = "100"
        }

        assertEquals("100", strategy.resolveVlan(subscription))
    }

    @Test
    fun `resolveVlan devuelve 1 desde subscription vlan`() {
        val subscription = subscriptionWithHostDevice(cloudCoreRouter(id = 1, vlanId = 1)).apply {
            vlan = "1"
        }

        assertEquals("1", strategy.resolveVlan(subscription))
    }

    @Test
    fun `resolveVlan payload 100 predomina aunque hostDevice tenga vlanId 1`() {
        val subscription = subscriptionWithHostDevice(cloudCoreRouter(id = 1, vlanId = 1)).apply {
            vlan = "100"
        }

        assertEquals("100", strategy.resolveVlan(subscription))
    }

    @Test
    fun `resolveVlan nulo o vacio falla sin fallback a hostDevice`() {
        val host = cloudCoreRouter(id = 8, vlanId = 100)
        val withoutVlan = subscriptionWithHostDevice(host)
        val blankVlan = subscriptionWithHostDevice(host).apply { vlan = "   " }

        assertThrows(IllegalArgumentException::class.java) {
            strategy.resolveVlan(withoutVlan)
        }
        assertThrows(IllegalArgumentException::class.java) {
            strategy.resolveVlan(blankVlan)
        }
    }

    @Test
    fun `resolveVlan falla con vlan invalida aunque hostDevice tenga vlanId`() {
        val subscription = subscriptionWithHostDevice(cloudCoreRouter(id = 8, vlanId = 100)).apply {
            vlan = "50"
        }

        assertThrows(IllegalArgumentException::class.java) {
            strategy.resolveVlan(subscription)
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("com.dscorp.wispadmin.wispadmin.extensions.ExtensionsKt")
    }

    @Test
    fun `resolveVlan falla cuando hostDevice esta deshabilitado`() {
        val subscription = subscriptionWithHostDevice(
            cloudCoreRouter(id = 8, vlanId = 100, disabled = true)
        ).apply { vlan = "100" }

        assertThrows(IllegalStateException::class.java) {
            strategy.resolveVlan(subscription)
        }
    }

    @Test
    fun `processInstallation does not throw when MikroTik TLS handshake fails`() {
        mockkStatic("com.dscorp.wispadmin.wispadmin.extensions.ExtensionsKt")
        val onuReuse = mockk<CancelledOnuReuseService>(relaxed = true)
        strategy = FiberInstallationStrategy(onuReuse)
        val host = cloudCoreRouter(id = 8, vlanId = 100)
        val subscription = subscriptionWithHostDevice(host).apply {
            vlan = "100"
            plan = Plan(id = 54, name = "f50", downloadSpeed = 50, uploadSpeed = 50)
            place = Place(id = 4, name = "Huacho")
        }
        every { any<NetworkDevice>().executeCommand(any()) } throws MikrotikCommandException(
            "rest PUT /rest/queue/simple: Remote host terminated the handshake"
        )

        val result = strategy.processInstallation(
            subscription = subscription,
            request = fiberRequest(),
            device = host,
            plan = subscription.plan!!,
            place = subscription.place!!
        )

        assertFalse(result.queueAdded)
        assertEquals(true, result.onuAuthorized)
    }

    @Test
    fun `processInstallation uses offline client ip as simple queue target`() {
        mockkStatic("com.dscorp.wispadmin.wispadmin.extensions.ExtensionsKt")
        val onuReuse = mockk<CancelledOnuReuseService>(relaxed = true)
        strategy = FiberInstallationStrategy(onuReuse)
        val host = cloudCoreRouter(id = 8, vlanId = 100)
        val subscription = subscriptionWithHostDevice(host).apply {
            vlan = "100"
            ip = "192.168.1.77"
            plan = Plan(id = 54, name = "f50", downloadSpeed = 50, uploadSpeed = 50)
            place = Place(id = 4, name = "Huacho")
        }
        val capturedArgs = slot<Map<String, String>>()
        every { any<NetworkDevice>().executeCommand(any()) } answers {
            val block = secondArg<(MikrotikSession) -> Unit>()
            val session = mockk<MikrotikSession>(relaxed = true)
            every { session.add("/queue/simple", capture(capturedArgs)) } just Runs
            block(session)
        }

        val result = strategy.processInstallation(
            subscription = subscription,
            request = fiberRequest().apply { clientIpAddress = "192.168.1.77" },
            device = host,
            plan = subscription.plan!!,
            place = subscription.place!!
        )

        assertTrue(result.queueAdded)
        assertEquals("192.168.1.77", capturedArgs.captured["target"])
    }

    private fun cloudCoreRouter(id: Int, vlanId: Int?, disabled: Boolean = false): NetworkDevice =
        NetworkDevice(
            id = id,
            name = "MK$id",
            ipAddress = if (id == 8) "38.224.231.4" else "38.224.231.2",
            networkDeviceType = NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER,
            vlanId = vlanId,
            disabled = disabled
        )

    private fun subscriptionWithHostDevice(hostDevice: NetworkDevice): Subscription =
        Subscription(
            id = 100,
            firstName = "Juan",
            lastName = "Perez",
            hostDevice = hostDevice,
            ip = "192.168.25.10",
            equipmentCondition = EquipmentCondition.LOAN
        )

    private fun fiberRequest() = SubscriptionRequest(
        firstName = "Juan",
        lastName = "Perez",
        dni = "12345678",
        address = "Calle 1",
        phone = "999888777",
        subscriptionDate = System.currentTimeMillis(),
        planId = 54,
        additionalDeviceIds = emptyList(),
        placeId = 4,
        location = GeoLocation(-11.0, -77.0),
        technicianId = 1,
        napBoxId = 63,
        hostDeviceId = 8,
        installationType = InstallationType.FIBER,
        onu = OnuDto(sn = "ALCL12345678", board = "1", olt_id = "1", onu = "1", onu_type_id = "1", onu_type_name = "HG8240H", pon_type = "gpon", port = "1"),
        clientRequestId = "offline-req-1",
        vlan = "100",
    )
}
