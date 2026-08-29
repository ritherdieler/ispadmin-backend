package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.wispadmin.extensions.NetworkDeviceConnection
import com.dscorp.wispadmin.wispadmin.extensions.NetworkDeviceConnectionHelper
import com.dscorp.wispadmin.wispadmin.extensions.NetworkDeviceConnectionManager
import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficCounterState
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficSample
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficCounterStateRepository
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import com.dscorp.wispadmin.traffic.repository.TrafficSourceRunRepository
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.ServiceStatus
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Optional

class SubscriptionTrafficPollServiceTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val sampleRepository = mockk<SubscriptionTrafficSampleRepository>(relaxed = true)
    private val counterStateRepository = mockk<SubscriptionTrafficCounterStateRepository>(relaxed = true)
    private val sourceRunRepository = mockk<TrafficSourceRunRepository>(relaxed = true)
    private val mikrotikClient = mockk<MikrotikClient>()
    private val routerOsClientProperties = RouterOsClientProperties()
    private val trafficProperties = TrafficProperties()

    private val service = SubscriptionTrafficPollService(
        subscriptionRepository = subscriptionRepository,
        sampleRepository = sampleRepository,
        counterStateRepository = counterStateRepository,
        sourceRunRepository = sourceRunRepository,
        mikrotikClient = mikrotikClient,
        routerOsClientProperties = routerOsClientProperties,
        trafficProperties = trafficProperties
    )

    @Test
    fun `pollTraffic establece baseline explicito en primer poll`() {
        val device = NetworkDevice(id = 9, name = "MK1", ipAddress = "10.0.0.1", username = "admin", password = "x")
        val subscription = Subscription(
            id = 42,
            ip = "10.10.10.20",
            hostDevice = device,
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        )
        every { subscriptionRepository.findForTrafficPolling() } returns listOf(subscription)
        every { counterStateRepository.findById(42) } returns Optional.empty()
        every { sampleRepository.findBySubscriptionIdAndBucketStart(any(), any()) } returns null
        every { sampleRepository.save(any()) } answers { firstArg() }
        every { sourceRunRepository.save(any()) } answers { firstArg() }
        val stateSlot = slot<SubscriptionTrafficCounterState>()
        every { counterStateRepository.save(capture(stateSlot)) } answers { firstArg() }
        val session = mockk<MikrotikSession>()
        every { session.print("/queue/simple", any(), any()) } returns listOf(
            mapOf("target" to "10.10.10.20/32", "bytes" to "1000/2000", "rate" to "500/1000")
        )
        every { session.print("/system/resource", any(), any()) } returns listOf(mapOf("uptime" to "1d2h"))
        every {
            mikrotikClient.withSession(any(), any<(MikrotikSession) -> Any>())
        } answers {
            val block = arg<(MikrotikSession) -> Any>(1)
            block(session)
        }

        val result = service.pollTraffic()

        assertEquals(1, result.devicesPolled)
        assertEquals(1, result.subscriptionsMatched)
        assertEquals(1, result.samplesWritten)
        assertEquals(2000L, stateSlot.captured.lastRxBytes)
        verify(atLeast = 1) { sampleRepository.save(match { it.sampleStatus.name == "BASELINE" }) }
    }

    @Test
    fun `pollTraffic persiste delta en segundo poll`() {
        val device = NetworkDevice(id = 9, name = "MK1", ipAddress = "10.0.0.1", username = "admin", password = "x")
        val subscription = Subscription(
            id = 42,
            ip = "10.10.10.20",
            hostDevice = device,
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        )
        every { subscriptionRepository.findForTrafficPolling() } returns listOf(subscription)
        every { counterStateRepository.findById(42) } returns Optional.of(
            SubscriptionTrafficCounterState(
                subscriptionId = 42,
                hostDeviceId = 9,
                lastRxBytes = 2000,
                lastTxBytes = 1000,
                lastRouterUptimeSeconds = 90000
            )
        )
        every { sampleRepository.findBySubscriptionIdAndBucketStart(any(), any()) } returns null
        every { sourceRunRepository.save(any()) } answers { firstArg() }
        val sampleSlot = slot<SubscriptionTrafficSample>()
        every { sampleRepository.save(capture(sampleSlot)) } answers { firstArg() }
        every { counterStateRepository.save(any()) } answers { firstArg() }
        val session = mockk<MikrotikSession>()
        every { session.print("/queue/simple", any(), any()) } returns listOf(
            mapOf("target" to "10.10.10.20/32", "bytes" to "1500/3500", "rate" to "500/1000")
        )
        every { session.print("/system/resource", any(), any()) } returns listOf(mapOf("uptime" to "1d2h"))
        every {
            mikrotikClient.withSession(any(), any<(MikrotikSession) -> Any>())
        } answers {
            val block = arg<(MikrotikSession) -> Any>(1)
            block(session)
        }

        val result = service.pollTraffic()

        assertEquals(1, result.samplesWritten)
        assertEquals(1500L, sampleSlot.captured.rxBytesDelta)
        assertEquals(500L, sampleSlot.captured.txBytesDelta)
    }

    @AfterEach
    fun tearDown() {
        val passthrough = mockk<NetworkDeviceConnectionHelper>()
        every { passthrough.getConnectionData(any()) } answers { firstArg() }
        NetworkDeviceConnectionManager.setHelper(passthrough)
    }

    @Test
    fun `pollTraffic ignora redirect dev mikrotik_test y usa host real`() {
        val helper = mockk<NetworkDeviceConnectionHelper>()
        every { helper.getConnectionData(any()) } answers {
            object : NetworkDeviceConnection {
                override val ipAddress: String? = "192.168.1.100"
                override val username: String? = "mock-user"
                override val password: String? = "mock-pass"
            }
        }
        NetworkDeviceConnectionManager.setHelper(helper)

        val device = NetworkDevice(
            id = 8,
            name = "Mikrotik CCR 2",
            ipAddress = "38.224.231.4",
            username = "gigafiber2023",
            password = "secret"
        )
        val subscription = Subscription(
            id = 615,
            ip = "192.168.25.92",
            hostDevice = device,
            serviceStatus = ServiceStatus.ACTIVE,
            equipmentCondition = EquipmentCondition.LOAN
        )
        every { subscriptionRepository.findForTrafficPolling() } returns listOf(subscription)
        every { counterStateRepository.findById(615) } returns Optional.empty()
        every { sampleRepository.findBySubscriptionIdAndBucketStart(any(), any()) } returns null
        every { sampleRepository.save(any()) } answers { firstArg() }
        every { sourceRunRepository.save(any()) } answers { firstArg() }
        every { counterStateRepository.save(any()) } answers { firstArg() }

        val session = mockk<MikrotikSession>()
        every { session.print("/queue/simple", any(), any()) } returns listOf(
            mapOf("target" to "192.168.25.92/32", "bytes" to "1000/2000", "rate" to "500/1000")
        )
        every { session.print("/system/resource", any(), any()) } returns listOf(mapOf("uptime" to "1d2h"))
        val deviceRefSlot = slot<MikrotikDeviceRef>()
        every {
            mikrotikClient.withSession(capture(deviceRefSlot), any<(MikrotikSession) -> Any>())
        } answers {
            val block = arg<(MikrotikSession) -> Any>(1)
            block(session)
        }

        service.pollTraffic()

        assertEquals("38.224.231.4", deviceRefSlot.captured.host)
        assertEquals("gigafiber2023", deviceRefSlot.captured.username)
    }

    @Test
    fun `pollTraffic omite cuando enabled false`() {
        val disabledService = SubscriptionTrafficPollService(
            subscriptionRepository,
            sampleRepository,
            counterStateRepository,
            sourceRunRepository,
            mikrotikClient,
            routerOsClientProperties,
            TrafficProperties(poll = TrafficProperties.PollProperties(enabled = false))
        )
        val result = disabledService.pollTraffic()
        assertEquals("traffic.poll.enabled=false", result.skippedReason)
    }
}
