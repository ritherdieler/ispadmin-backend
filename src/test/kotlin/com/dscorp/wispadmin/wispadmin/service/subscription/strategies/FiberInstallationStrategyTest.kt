package com.dscorp.wispadmin.wispadmin.service.subscription.strategies

import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.service.CancelledOnuReuseService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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
    fun `resolveVlan devuelve 100 para MK2 piloto`() {
        val subscription = subscriptionWithHostDevice(cloudCoreRouter(id = 8, vlanId = 100))

        assertEquals("100", strategy.resolveVlan(subscription))
    }

    @Test
    fun `resolveVlan devuelve 1 para MK1 legacy`() {
        val subscription = subscriptionWithHostDevice(cloudCoreRouter(id = 1, vlanId = 1))

        assertEquals("1", strategy.resolveVlan(subscription))
    }

    @Test
    fun `resolveVlan falla cuando CLOUD_CORE_ROUTER no tiene vlanId`() {
        val subscription = subscriptionWithHostDevice(cloudCoreRouter(id = 8, vlanId = null))

        assertThrows(IllegalStateException::class.java) {
            strategy.resolveVlan(subscription)
        }
    }

    @Test
    fun `resolveVlan falla cuando hostDevice esta deshabilitado`() {
        val subscription = subscriptionWithHostDevice(
            cloudCoreRouter(id = 8, vlanId = 100, disabled = true)
        )

        assertThrows(IllegalStateException::class.java) {
            strategy.resolveVlan(subscription)
        }
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
}
