package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.entity.TrafficCounterState
import com.dscorp.wispadmin.traffic.entity.TrafficRouter
import com.dscorp.wispadmin.traffic.entity.SubscriptionTrafficSample
import com.dscorp.wispadmin.traffic.port.TrafficDirectoryPort
import com.dscorp.wispadmin.traffic.port.TrafficDirectoryTarget
import com.dscorp.wispadmin.traffic.repository.SubscriptionTrafficSampleRepository
import com.dscorp.wispadmin.traffic.repository.TrafficCounterStateRepository
import com.dscorp.wispadmin.traffic.repository.TrafficRouterRepository
import com.dscorp.wispadmin.traffic.repository.TrafficSourceRunRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Optional

class SubscriptionTrafficPollServiceTest {

    private val routerRepository = mockk<TrafficRouterRepository>()
    private val directory = mockk<TrafficDirectoryPort>()
    private val sampleRepository = mockk<SubscriptionTrafficSampleRepository>(relaxed = true)
    private val counterStateRepository = mockk<TrafficCounterStateRepository>(relaxed = true)
    private val sourceRunRepository = mockk<TrafficSourceRunRepository>(relaxed = true)
    private val mikrotikClient = mockk<MikrotikClient>()
    private val routerOsClientProperties = RouterOsClientProperties()
    private val trafficProperties = TrafficProperties()

    private val service = SubscriptionTrafficPollService(
        routerRepository,
        directory,
        sampleRepository,
        counterStateRepository,
        sourceRunRepository,
        mikrotikClient,
        routerOsClientProperties,
        trafficProperties,
    )

    private val router = TrafficRouter(id = 9, name = "MK1", host = "10.0.0.1", username = "admin", password = "x")

    @Test
    fun `pollTraffic establece baseline explicito en primer poll y etiqueta subscriptionId`() {
        every { routerRepository.findByEnabledTrue() } returns listOf(router)
        every { directory.list() } returns listOf(TrafficDirectoryTarget(42, "10.10.10.20", routerHint = 9))
        every { counterStateRepository.findById("10.10.10.20") } returns Optional.empty()
        every { sampleRepository.findByClientIpAndBucketStart(any(), any()) } returns null
        every { sampleRepository.save(any()) } answers { firstArg() }
        every { sourceRunRepository.save(any()) } answers { firstArg() }
        val stateSlot = slot<TrafficCounterState>()
        every { counterStateRepository.save(capture(stateSlot)) } answers { firstArg() }
        stubSession(
            mapOf("target" to "10.10.10.20/32", "bytes" to "1000/2000", "rate" to "500/1000", "max-limit" to "10M/20M"),
        )

        val result = service.pollTraffic()

        assertEquals(1, result.devicesPolled)
        assertEquals(1, result.subscriptionsMatched)
        assertEquals(1, result.samplesWritten)
        assertEquals(2000L, stateSlot.captured.lastRxBytes)
        assertEquals(42, stateSlot.captured.subscriptionId)
        verify(atLeast = 1) {
            sampleRepository.save(match { it.sampleStatus.name == "BASELINE" && it.clientIp == "10.10.10.20" && it.subscriptionId == 42 })
        }
    }

    @Test
    fun `pollTraffic persiste delta en segundo poll`() {
        every { routerRepository.findByEnabledTrue() } returns listOf(router)
        every { directory.list() } returns listOf(TrafficDirectoryTarget(42, "10.10.10.20", routerHint = 9))
        every { counterStateRepository.findById("10.10.10.20") } returns Optional.of(
            TrafficCounterState(
                clientIp = "10.10.10.20",
                subscriptionId = 42,
                hostDeviceId = 9,
                lastRxBytes = 2000,
                lastTxBytes = 1000,
                lastRouterUptimeSeconds = 90000,
            ),
        )
        every { sampleRepository.findByClientIpAndBucketStart(any(), any()) } returns null
        every { sourceRunRepository.save(any()) } answers { firstArg() }
        val sampleSlot = slot<SubscriptionTrafficSample>()
        every { sampleRepository.save(capture(sampleSlot)) } answers { firstArg() }
        every { counterStateRepository.save(any()) } answers { firstArg() }
        stubSession(mapOf("target" to "10.10.10.20/32", "bytes" to "1500/3500", "rate" to "500/1000"))

        val result = service.pollTraffic()

        assertEquals(1, result.samplesWritten)
        assertEquals(1500L, sampleSlot.captured.rxBytesDelta)
        assertEquals(500L, sampleSlot.captured.txBytesDelta)
        assertEquals("10.10.10.20", sampleSlot.captured.clientIp)
    }

    @Test
    fun `pollTraffic publica traffic latest cuando hay delta OK`() {
        val bus = com.dscorp.wispadmin.events.RecordingEventBus()
        val publishing = SubscriptionTrafficPollService(
            routerRepository,
            directory,
            sampleRepository,
            counterStateRepository,
            sourceRunRepository,
            mikrotikClient,
            routerOsClientProperties,
            trafficProperties,
            bus,
        )
        every { routerRepository.findByEnabledTrue() } returns listOf(router)
        every { directory.list() } returns listOf(TrafficDirectoryTarget(42, "10.10.10.20", routerHint = 9))
        every { counterStateRepository.findById("10.10.10.20") } returns Optional.of(
            TrafficCounterState(
                clientIp = "10.10.10.20",
                subscriptionId = 42,
                hostDeviceId = 9,
                lastRxBytes = 2000,
                lastTxBytes = 1000,
                lastRouterUptimeSeconds = 90000,
            ),
        )
        every { sampleRepository.findByClientIpAndBucketStart(any(), any()) } returns null
        every { sourceRunRepository.save(any()) } answers { firstArg() }
        every { sampleRepository.save(any()) } answers { firstArg() }
        every { counterStateRepository.save(any()) } answers { firstArg() }
        stubSession(mapOf("target" to "10.10.10.20/32", "bytes" to "1500/3500", "rate" to "500/1000"))

        publishing.pollTraffic()

        assertEquals(1, bus.published.count { it.type == com.dscorp.wispadmin.events.PlatformEventTypes.TRAFFIC_LATEST && it.subscriptionId == 42 })
        assertEquals(1, bus.published.count { it.type == com.dscorp.wispadmin.events.PlatformEventTypes.TRAFFIC_POLL_RUN })
    }

    @Test
    fun `pollTraffic sigue si el directorio falla y deja subscriptionId nulo`() {
        every { routerRepository.findByEnabledTrue() } returns listOf(router)
        every { directory.list() } throws IllegalStateException("directory down")
        every { counterStateRepository.findById("10.10.10.20") } returns Optional.empty()
        every { sampleRepository.findByClientIpAndBucketStart(any(), any()) } returns null
        every { sampleRepository.save(any()) } answers { firstArg() }
        every { sourceRunRepository.save(any()) } answers { firstArg() }
        every { counterStateRepository.save(any()) } answers { firstArg() }
        stubSession(mapOf("target" to "10.10.10.20/32", "bytes" to "1000/2000"))

        val result = service.pollTraffic()

        assertEquals(1, result.samplesWritten)
        verify { sampleRepository.save(match { it.clientIp == "10.10.10.20" && it.subscriptionId == null }) }
    }

    @Test
    fun `pollTraffic usa host del traffic_router`() {
        every { routerRepository.findByEnabledTrue() } returns listOf(
            TrafficRouter(id = 8, name = "CCR2", host = "38.224.231.4", username = "gigafiber2023", password = "secret"),
        )
        every { directory.list() } returns emptyList()
        every { counterStateRepository.findById(any()) } returns Optional.empty()
        every { sampleRepository.findByClientIpAndBucketStart(any(), any()) } returns null
        every { sampleRepository.save(any()) } answers { firstArg() }
        every { sourceRunRepository.save(any()) } answers { firstArg() }
        every { counterStateRepository.save(any()) } answers { firstArg() }
        val session = mockk<MikrotikSession>()
        every { session.print("/queue/simple", any(), any()) } returns listOf(
            mapOf("target" to "192.168.25.92/32", "bytes" to "1000/2000"),
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
            routerRepository,
            directory,
            sampleRepository,
            counterStateRepository,
            sourceRunRepository,
            mikrotikClient,
            routerOsClientProperties,
            TrafficProperties().apply { poll.enabled = false },
        )
        val result = disabledService.pollTraffic()
        assertEquals("traffic.poll.enabled=false", result.skippedReason)
        assertNull(result.error)
    }

    @Test
    fun `pollTraffic continues when one sample persist fails`() {
        every { routerRepository.findByEnabledTrue() } returns listOf(router)
        every { directory.list() } returns emptyList()
        every { counterStateRepository.findById(any()) } returns Optional.empty()
        every { sampleRepository.findByClientIpAndBucketStart(any(), any()) } returns null
        every { sampleRepository.save(any()) } answers {
            val sample = firstArg<SubscriptionTrafficSample>()
            if (sample.clientIp == "10.10.10.1") {
                throw RuntimeException("could not execute statement")
            }
            sample
        }
        every { sourceRunRepository.save(any()) } answers { firstArg() }
        every { counterStateRepository.save(any()) } answers { firstArg() }
        val session = mockk<MikrotikSession>()
        every { session.print("/queue/simple", any(), any()) } returns listOf(
            mapOf("target" to "10.10.10.1/32", "bytes" to "1000/2000"),
            mapOf("target" to "10.10.10.20/32", "bytes" to "1000/2000"),
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
        assertEquals(1, result.samplesWritten)
        assertNull(result.error)
        verify { sampleRepository.save(match { it.clientIp == "10.10.10.20" }) }
    }

    private fun stubSession(queue: Map<String, String>) {
        val session = mockk<MikrotikSession>()
        every { session.print("/queue/simple", any(), any()) } returns listOf(queue)
        every { session.print("/system/resource", any(), any()) } returns listOf(mapOf("uptime" to "1d2h"))
        every {
            mikrotikClient.withSession(any(), any<(MikrotikSession) -> Any>())
        } answers {
            val block = arg<(MikrotikSession) -> Any>(1)
            block(session)
        }
    }
}
