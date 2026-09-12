package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Plan
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.extensions.executeCommand
import com.dscorp.wispadmin.wispadmin.repository.ErrorLogRepository
import com.dscorp.wispadmin.wispadmin.service.mikrotik.IMikroTikService
import com.dscorp.wispadmin.wispadmin.service.mikrotik.PppoeAccessService
import com.dscorp.wispadmin.routeros.port.MikrotikTimeoutException
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MikrotikPaymentReactivationHandlerTest {

    private val errorLogRepository = mockk<ErrorLogRepository>(relaxed = true)
    private val mikrotikService = mockk<IMikroTikService>(relaxed = true)
    private val pppoeAccessService = mockk<PppoeAccessService>(relaxed = true)
    private lateinit var handler: MikrotikPaymentReactivationHandler

    @BeforeEach
    fun setUp() {
        handler = MikrotikPaymentReactivationHandler(errorLogRepository, mikrotikService, pppoeAccessService)
        mockkStatic("com.dscorp.wispadmin.wispadmin.extensions.ExtensionsKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("com.dscorp.wispadmin.wispadmin.extensions.ExtensionsKt")
    }

    @Test
    fun reactivateFromDebtorsList_whenMikrotikFails_persistsErrorLogWithoutPropagating() {
        every { any<NetworkDevice>().executeCommand(any()) } throws
            MikrotikTimeoutException("rest POST /rest/ip/firewall/address-list/print: Read timed out")
        every { errorLogRepository.save(any()) } answers { firstArg() }

        assertDoesNotThrow { handler.reactivateFromDebtorsList(sampleSubscription()) }

        verify(exactly = 1) { errorLogRepository.save(any()) }
    }

    @Test
    fun reactivateFromDebtorsList_withoutHostDevice_doesNotWriteErrorLog() {
        handler.reactivateFromDebtorsList(
            Subscription(
                id = 1,
                firstName = "A",
                lastName = "B",
                equipmentCondition = EquipmentCondition.LOAN,
            ).apply {
                ip = "10.1.1.5"
                hostDevice = null
            }
        )
        verify(exactly = 0) { errorLogRepository.save(any()) }
        verify(exactly = 0) { pppoeAccessService.restore(any(), any()) }
    }

    @Test
    fun reactivateFromDebtorsList_forPppoeDynamic_restoresPlanProfile() {
        val host = cloudCoreRouter()
        val subscription = sampleSubscription().apply {
            accessMode = AccessMode.PPPOE_DYNAMIC
            pppoeUsername = "gf1686"
            ip = null
            hostDevice = host
        }
        every { pppoeAccessService.restore(subscription, host) } returns true

        handler.reactivateFromDebtorsList(subscription)

        verify(exactly = 1) { pppoeAccessService.restore(subscription, host) }
        verify(exactly = 0) { mikrotikService.removeIpFromAllCutLists(any(), any()) }
    }

    @Test
    fun reactivateFromDebtorsList_forPppoeFixed_restoresProfileAndRemovesIpFromCutLists() {
        val host = cloudCoreRouter()
        val subscription = sampleSubscription().apply {
            accessMode = AccessMode.PPPOE_FIXED
            pppoeUsername = "ANTONYCHEROLUBIO"
            ip = "192.168.210.231"
            hostDevice = host
        }
        every { any<NetworkDevice>().executeCommand(any()) } answers {
            val block = secondArg<(com.dscorp.wispadmin.routeros.port.MikrotikSession) -> Unit>()
            block(mockk(relaxed = true))
        }
        every { pppoeAccessService.restore(subscription, host) } returns true

        handler.reactivateFromDebtorsList(subscription)

        verify(exactly = 1) { pppoeAccessService.restore(subscription, host) }
        verify(exactly = 1) { mikrotikService.removeIpFromAllCutLists(any(), "192.168.210.231") }
    }

    @Test
    fun reactivateFromDebtorsList_forStaticIp_onlyRemovesAddressListEntries() {
        val host = cloudCoreRouter()
        val subscription = sampleSubscription().apply {
            accessMode = AccessMode.STATIC_IP
            hostDevice = host
        }
        every { any<NetworkDevice>().executeCommand(any()) } answers {
            val block = secondArg<(com.dscorp.wispadmin.routeros.port.MikrotikSession) -> Unit>()
            block(mockk(relaxed = true))
        }

        handler.reactivateFromDebtorsList(subscription)

        verify(exactly = 0) { pppoeAccessService.restore(any(), any()) }
        verify(exactly = 1) { mikrotikService.removeIpFromAllCutLists(any(), "10.11.104.50") }
    }

    private fun sampleSubscription(): Subscription {
        return Subscription(
            id = 1686,
            firstName = "Juan",
            lastName = "Perez",
            equipmentCondition = EquipmentCondition.LOAN,
        ).apply {
            ip = "10.11.104.50"
            hostDevice = cloudCoreRouter()
            plan = Plan(id = 54, name = "f200", downloadSpeed = 200, uploadSpeed = 200)
        }
    }

    private fun cloudCoreRouter() = NetworkDevice(
        id = 8,
        name = "MK",
        ipAddress = "38.224.231.4",
        username = "admin",
        password = "secret",
        networkDeviceType = NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER,
        vlanId = 100
    )
}
