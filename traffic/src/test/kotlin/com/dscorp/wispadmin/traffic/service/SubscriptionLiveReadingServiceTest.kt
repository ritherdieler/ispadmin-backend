package com.dscorp.wispadmin.traffic.service

import com.dscorp.wispadmin.routeros.config.RouterOsClientProperties
import com.dscorp.wispadmin.routeros.port.MikrotikClient
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.dscorp.wispadmin.traffic.entity.TrafficRouter
import com.dscorp.wispadmin.traffic.repository.TrafficRouterRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class SubscriptionLiveReadingServiceTest {

    private val routers = mockk<TrafficRouterRepository>()
    private val mikrotik = mockk<MikrotikClient>()
    private val session = mockk<MikrotikSession>()
    private val clock = Clock.fixed(Instant.parse("2026-09-15T17:40:00Z"), ZoneOffset.UTC)
    private val queueCache = SimpleQueueSnapshotCache()
    private val service = SubscriptionLiveReadingService(
        routers,
        mikrotik,
        RouterOsClientProperties(),
        queueCache,
    ).also { it.clock = clock }
    private val router = TrafficRouter(id = 8, name = "MK2", host = "10.0.0.1", username = "u", password = "p")

    @Test
    fun `missing host returns unavailable none and does not open RouterOS`() {
        val reading = service.read(
            LiveReadingIdentity(subscriptionId = 6, accessMode = "PPPOE_DYNAMIC", hostDeviceId = null),
        )

        assertFalse(reading.available)
        assertEquals("NONE", reading.source)
        verify(exactly = 0) { mikrotik.withSession(any<MikrotikDeviceRef>(), any<(MikrotikSession) -> Any>()) }
    }

    @Test
    fun `static ip leftover pppoe session at same ip reads pppoe-in not empty queue`() {
        stubRouterAndSession()
        every { session.print("/ppp/active", any(), any()) } returns listOf(mapOf("name" to "gf5", "address" to "10.64.0.11"))
        every { session.print("/queue/simple", any(), any()) } returns emptyList()
        every {
            session.call("/interface/monitor-traffic", mapOf("interface" to "<pppoe-gf5>", "once" to ""))
        } returns listOf(mapOf("rx-bits-per-second" to "25096", "tx-bits-per-second" to "29848"))

        val reading = service.read(
            LiveReadingIdentity(
                subscriptionId = 5,
                accessMode = "STATIC_IP",
                ip = "10.64.0.11",
                hostDeviceId = 8,
            ),
        )

        assertTrue(reading.available)
        assertEquals("PPPOE", reading.source)
        assertEquals("gf5", reading.pppoe)
        assertEquals(29848L, reading.downloadBps)
        assertEquals(25096L, reading.uploadBps)
        verify(exactly = 0) { session.print("/interface", any(), any()) }
    }

    @Test
    fun `static ip queue matched by ip returns queue rates`() {
        stubRouterAndSession()
        every { session.print("/ppp/active", any(), any()) } returns emptyList()
        every { session.print("/queue/simple", any(), any()) } returns listOf(
            mapOf(
                "name" to "id:7, usuario:Static",
                "target" to "192.168.250.16/32",
                "rate" to "145000/1800000",
                "bytes" to "152043520/2147483648",
            ),
        )

        val reading = service.read(
            LiveReadingIdentity(
                subscriptionId = 7,
                accessMode = "STATIC_IP",
                ip = "192.168.250.16",
                hostDeviceId = 8,
            ),
        )

        assertTrue(reading.available)
        assertEquals("QUEUE", reading.source)
        assertNull(reading.pppoe)
        assertEquals(1_800_000L, reading.downloadBps)
        assertEquals(145_000L, reading.uploadBps)
        verify(exactly = 0) { session.print("/interface", any(), any()) }
        verify(exactly = 0) { session.call(any(), any()) }
    }

    @Test
    fun `pppoe dynamic reads active pppoe-in not residual queue rate`() {
        stubRouterAndSession()
        every { session.print("/interface", any(), any()) } returns listOf(
            mapOf("name" to "ether1", "type" to "ether", "rx-byte" to "9", "tx-byte" to "3"),
            mapOf("name" to "<pppoe-gf6>", "type" to "pppoe-in", "rx-byte" to "851540868", "tx-byte" to "3134442266"),
        )
        every {
            session.call("/interface/monitor-traffic", mapOf("interface" to "<pppoe-gf6>", "once" to ""))
        } returns listOf(mapOf("rx-bits-per-second" to "145000", "tx-bits-per-second" to "8500000"))

        val reading = service.read(
            LiveReadingIdentity(
                subscriptionId = 6,
                accessMode = "PPPOE_DYNAMIC",
                pppoeUsername = "gf6",
                hostDeviceId = 8,
            ),
        )

        assertEquals("PPPOE", reading.source)
        assertEquals("gf6", reading.pppoe)
        assertEquals(8_500_000L, reading.downloadBps)
        assertEquals(145_000L, reading.uploadBps)
        assertEquals(3_134_442_266L, reading.rxBytes)
        verify(exactly = 0) { session.print("/queue/simple", any(), any()) }
    }

    @Test
    fun `pppoe dynamic without session does not use simple queue`() {
        stubRouterAndSession()
        every { session.print("/interface", any(), any()) } returns listOf(
            mapOf("name" to "ether1", "type" to "ether", "rx-byte" to "9", "tx-byte" to "3"),
        )

        val reading = service.read(
            LiveReadingIdentity(
                subscriptionId = 6,
                accessMode = "PPPOE_DYNAMIC",
                pppoeUsername = "gf6",
                hostDeviceId = 8,
            ),
        )

        assertFalse(reading.available)
        assertEquals("NONE", reading.source)
        verify(exactly = 0) { session.print("/queue/simple", any(), any()) }
    }

    @Test
    fun `pppoe fixed still reads provisioned simple queue`() {
        stubRouterAndSession()
        every { session.print("/ppp/active", any(), any()) } returns emptyList()
        every { session.print("/queue/simple", any(), any()) } returns listOf(
            mapOf(
                "name" to "[stg] id:8, usuario:Fixed",
                "target" to "10.64.0.20/32",
                "rate" to "2000/4000",
                "bytes" to "11/22",
            ),
        )

        val reading = service.read(
            LiveReadingIdentity(
                subscriptionId = 8,
                accessMode = "PPPOE_FIXED",
                ip = "10.64.0.20",
                pppoeUsername = "gf8",
                hostDeviceId = 8,
                envTag = "stg",
            ),
        )

        assertEquals("QUEUE", reading.source)
        assertEquals(4000L, reading.downloadBps)
        assertEquals(2000L, reading.uploadBps)
        verify(exactly = 0) { session.print("/interface", any(), any()) }
    }

    @Test
    fun `static ip leftover plus matching queue uses queue not leftover pppoe`() {
        stubRouterAndSession()
        every { session.print("/ppp/active", any(), any()) } returns listOf(mapOf("name" to "gf5", "address" to "10.64.0.11"))
        every { session.print("/interface", any(), any()) } returns listOf(
            mapOf("name" to "<pppoe-gf5>", "type" to "pppoe-in", "rx-byte" to "1", "tx-byte" to "2"),
        )
        every { session.print("/queue/simple", any(), any()) } returns listOf(
            mapOf(
                "name" to "id:5, usuario:Static",
                "target" to "10.64.0.11/32",
                "rate" to "1000/2000",
                "bytes" to "10/20",
            ),
        )

        val reading = service.read(
            LiveReadingIdentity(
                subscriptionId = 5,
                accessMode = "STATIC_IP",
                ip = "10.64.0.11",
                hostDeviceId = 8,
            ),
        )

        assertEquals("QUEUE", reading.source)
        assertEquals(2000L, reading.downloadBps)
        assertEquals(1000L, reading.uploadBps)
        verify(exactly = 0) { session.print("/interface", any(), any()) }
        verify(exactly = 0) { session.call(any(), any()) }
    }

    @Test
    fun `leftover pppoe user from active does not print interface`() {
        stubRouterAndSession()
        every { session.print("/ppp/active", any(), any()) } returns listOf(mapOf("name" to "gf5", "address" to "10.64.0.11"))
        every { session.print("/queue/simple", any(), any()) } returns emptyList()
        every {
            session.call("/interface/monitor-traffic", mapOf("interface" to "<pppoe-gf5>", "once" to ""))
        } returns listOf(mapOf("rx-bits-per-second" to "25096", "tx-bits-per-second" to "29848"))

        val reading = service.read(
            LiveReadingIdentity(
                subscriptionId = 5,
                accessMode = "STATIC_IP",
                ip = "10.64.0.11",
                hostDeviceId = 8,
            ),
        )

        assertTrue(reading.available)
        assertEquals("PPPOE", reading.source)
        assertEquals("gf5", reading.pppoe)
        assertEquals(29848L, reading.downloadBps)
        assertEquals(25096L, reading.uploadBps)
        verify(exactly = 0) { session.print("/interface", any(), any()) }
    }

    @Test
    fun `queue snapshot cache hit does not print simple queue twice`() {
        stubRouterAndSession()
        every { session.print("/ppp/active", any(), any()) } returns emptyList()
        every { session.print("/queue/simple", any(), any()) } returns listOf(
            mapOf(
                "name" to "id:7, usuario:Static",
                "target" to "192.168.250.16/32",
                "rate" to "145000/1800000",
                "bytes" to "152043520/2147483648",
            ),
        )

        val identity = LiveReadingIdentity(
            subscriptionId = 7,
            accessMode = "STATIC_IP",
            ip = "192.168.250.16",
            hostDeviceId = 8,
        )
        service.read(identity)
        service.read(identity)

        verify(exactly = 1) { session.print("/queue/simple", any(), any()) }
    }

    @Test
    fun `blank accessMode uses simple queue not pppoe dynamic`() {
        stubRouterAndSession()
        every { session.print("/ppp/active", any(), any()) } returns emptyList()
        every { session.print("/queue/simple", any(), any()) } returns listOf(
            mapOf(
                "name" to "id:7, usuario:Static",
                "target" to "192.168.250.16/32",
                "rate" to "145000/1800000",
                "bytes" to "10/20",
            ),
        )

        val reading = service.read(
            LiveReadingIdentity(
                subscriptionId = 7,
                accessMode = "",
                ip = "192.168.250.16",
                hostDeviceId = 8,
            ),
        )

        assertEquals("QUEUE", reading.source)
        verify(exactly = 0) { session.print("/interface", any(), any()) }
    }

    @Test
    fun `mikrotik error returns unavailable none`() {
        every { routers.findById(8) } returns Optional.of(router)
        every { mikrotik.withSession(any<MikrotikDeviceRef>(), any<(MikrotikSession) -> Any>()) } throws
            IllegalStateException("timeout")

        val reading = service.read(
            LiveReadingIdentity(subscriptionId = 6, accessMode = "PPPOE_DYNAMIC", hostDeviceId = 8),
        )

        assertFalse(reading.available)
        assertEquals("NONE", reading.source)
    }

    private fun stubRouterAndSession() {
        every { routers.findById(8) } returns Optional.of(router)
        every {
            mikrotik.withSession(any<MikrotikDeviceRef>(), any<(MikrotikSession) -> Any>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val block = invocation.args[1] as (MikrotikSession) -> Any
            block(session)
        }
    }
}
