package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
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

    private val repository = mockk<SubscriptionRepository>()
    private val mikrotik = mockk<MikroTikConnectionService>()
    private val clock = Clock.fixed(Instant.parse("2026-09-15T17:40:00Z"), ZoneOffset.UTC)
    private val environment = GigafiberEnvironmentProperties()
    private val service = SubscriptionLiveReadingService(repository, mikrotik, environment).also { it.clock = clock }
    private val host = NetworkDevice(
        id = 8,
        name = "MK2",
        networkDeviceType = NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER,
    )

    @Test
    fun `missing subscription returns unavailable none`() {
        every { repository.findById(6) } returns Optional.empty()

        val reading = service.read(6)

        assertEquals(6, reading.subscriptionId)
        assertFalse(reading.available)
        assertEquals("NONE", reading.source)
        assertEquals("2026-09-15T17:40:00Z", reading.timestamp)
        assertEquals(0, reading.downloadBps)
        assertEquals(0, reading.uploadBps)
    }

    @Test
    fun `subscription without host returns unavailable none`() {
        every { repository.findById(6) } returns Optional.of(subscription(hostDevice = null))

        val reading = service.read(6)

        assertFalse(reading.available)
        assertEquals("NONE", reading.source)
    }

    @Test
    fun `static ip queue matched by ip returns queue rates`() {
        every { repository.findById(7) } returns Optional.of(
            subscription(id = 7, ip = "192.168.250.16", pppoeUsername = null, accessMode = AccessMode.STATIC_IP)
        )
        every { mikrotik.printOnDevice(host, "/queue/simple") } returns listOf(
            mapOf(
                "name" to "id:7, usuario:Static",
                "target" to "192.168.250.16/32",
                "rate" to "145000/1800000",
                "bytes" to "152043520/2147483648",
            )
        )

        val reading = service.read(7)

        assertTrue(reading.available)
        assertEquals("QUEUE", reading.source)
        assertNull(reading.pppoe)
        assertEquals(1_800_000L, reading.downloadBps)
        assertEquals(145_000L, reading.uploadBps)
        assertEquals(2_147_483_648L, reading.rxBytes)
        assertEquals(152_043_520L, reading.txBytes)
        verify(exactly = 0) { mikrotik.printOnDevice(host, "/interface") }
        verify(exactly = 0) { mikrotik.callOnDevice(any(), any(), any()) }
    }

    @Test
    fun `pppoe dynamic reads active pppoe-in not residual queue rate`() {
        every { repository.findById(6) } returns Optional.of(subscription())
        every { mikrotik.printOnDevice(host, "/interface") } returns listOf(
            mapOf("name" to "ether1", "type" to "ether", "rx-byte" to "9", "tx-byte" to "3"),
            mapOf("name" to "<pppoe-gf6>", "type" to "pppoe-in", "rx-byte" to "851540868", "tx-byte" to "3134442266"),
        )
        every {
            mikrotik.callOnDevice(
                host,
                "/interface/monitor-traffic",
                mapOf("interface" to "<pppoe-gf6>", "once" to ""),
            )
        } returns listOf(
            mapOf("name" to "<pppoe-gf6>", "rx-bits-per-second" to "145000", "tx-bits-per-second" to "8500000"),
        )

        val reading = service.read(6)

        assertTrue(reading.available)
        assertEquals("PPPOE", reading.source)
        assertEquals("gf6", reading.pppoe)
        assertEquals(8_500_000L, reading.downloadBps)
        assertEquals(145_000L, reading.uploadBps)
        assertEquals(3_134_442_266L, reading.rxBytes)
        assertEquals(851_540_868L, reading.txBytes)
        verify(exactly = 0) { mikrotik.printOnDevice(host, "/queue/simple") }
    }

    @Test
    fun `pppoe dynamic ignores prod id queue and dynamic pppoe queue rate`() {
        every { repository.findById(6) } returns Optional.of(subscription())
        every { mikrotik.printOnDevice(host, "/queue/simple") } returns listOf(
            mapOf(
                "name" to "id:6, usuario:CINTIA ESCOBAL",
                "target" to "192.168.30.23/32",
                "rate" to "840/560",
                "bytes" to "6975054651/106361469423",
            ),
            mapOf(
                "name" to "<pppoe-gf6>",
                "target" to "<pppoe-gf6>",
                "rate" to "157592/268136",
                "bytes" to "847948720/3127948252",
            ),
        )
        every { mikrotik.printOnDevice(host, "/interface") } returns listOf(
            mapOf("name" to "<pppoe-gf6>", "type" to "pppoe-in", "rx-byte" to "851540868", "tx-byte" to "3134442266"),
        )
        every {
            mikrotik.callOnDevice(host, "/interface/monitor-traffic", mapOf("interface" to "<pppoe-gf6>", "once" to ""))
        } returns listOf(
            mapOf("rx-bits-per-second" to "412000", "tx-bits-per-second" to "12500000"),
        )

        val reading = service.read(6)

        assertEquals("PPPOE", reading.source)
        assertEquals("gf6", reading.pppoe)
        assertEquals(12_500_000L, reading.downloadBps)
        assertEquals(412_000L, reading.uploadBps)
        assertEquals(3_134_442_266L, reading.rxBytes)
        assertEquals(851_540_868L, reading.txBytes)
        verify(exactly = 0) { mikrotik.printOnDevice(host, "/queue/simple") }
    }

    @Test
    fun `pppoe-in name without brackets still matches`() {
        every { repository.findById(6) } returns Optional.of(subscription())
        every { mikrotik.printOnDevice(host, "/interface") } returns listOf(
            mapOf("name" to "pppoe-gf6", "type" to "pppoe-in", "rx-byte" to "8", "tx-byte" to "22"),
        )
        every {
            mikrotik.callOnDevice(host, "/interface/monitor-traffic", mapOf("interface" to "pppoe-gf6", "once" to ""))
        } returns listOf(
            mapOf("rx-bits-per-second" to "1000", "tx-bits-per-second" to "2000"),
        )

        val reading = service.read(6)

        assertEquals("PPPOE", reading.source)
        assertEquals("gf6", reading.pppoe)
        assertEquals(2000L, reading.downloadBps)
        assertEquals(1000L, reading.uploadBps)
        assertEquals(22L, reading.rxBytes)
        assertEquals(8L, reading.txBytes)
    }

    @Test
    fun `monitor-traffic failure still returns pppoe counters`() {
        every { repository.findById(6) } returns Optional.of(subscription())
        every { mikrotik.printOnDevice(host, "/interface") } returns listOf(
            mapOf("name" to "<pppoe-gf6>", "type" to "pppoe-in", "rx-byte" to "851540868", "tx-byte" to "3134442266"),
        )
        every {
            mikrotik.callOnDevice(host, "/interface/monitor-traffic", mapOf("interface" to "<pppoe-gf6>", "once" to ""))
        } throws IllegalStateException("timeout")

        val reading = service.read(6)

        assertEquals("PPPOE", reading.source)
        assertEquals("gf6", reading.pppoe)
        assertEquals(0L, reading.downloadBps)
        assertEquals(0L, reading.uploadBps)
        assertEquals(3_134_442_266L, reading.rxBytes)
        assertEquals(851_540_868L, reading.txBytes)
    }

    @Test
    fun `pppoe dynamic without session does not use simple queue`() {
        every { repository.findById(6) } returns Optional.of(subscription())
        every { mikrotik.printOnDevice(host, "/interface") } returns listOf(
            mapOf("name" to "ether1", "type" to "ether", "rx-byte" to "9", "tx-byte" to "3"),
        )
        every { mikrotik.printOnDevice(host, "/queue/simple") } returns listOf(
            mapOf(
                "name" to "<pppoe-gf6>",
                "target" to "<pppoe-gf6>",
                "rate" to "157592/268136",
                "bytes" to "847948720/3127948252",
            ),
        )

        val reading = service.read(6)

        assertFalse(reading.available)
        assertEquals("NONE", reading.source)
        assertNull(reading.pppoe)
        verify(exactly = 0) { mikrotik.printOnDevice(host, "/queue/simple") }
    }

    @Test
    fun `no pppoe session and static queue miss returns none`() {
        every { repository.findById(7) } returns Optional.of(
            subscription(id = 7, ip = "192.168.250.99", pppoeUsername = null, accessMode = AccessMode.STATIC_IP)
        )
        every { mikrotik.printOnDevice(host, "/queue/simple") } returns emptyList()
        every { mikrotik.printOnDevice(host, "/interface") } returns listOf(
            mapOf("name" to "ether1", "type" to "ether", "rx-byte" to "9", "tx-byte" to "3"),
        )

        val reading = service.read(7)

        assertFalse(reading.available)
        assertEquals("NONE", reading.source)
        assertNull(reading.pppoe)
    }

    @Test
    fun `mikrotik error returns unavailable none`() {
        every { repository.findById(6) } returns Optional.of(subscription())
        every { mikrotik.printOnDevice(host, "/interface") } throws IllegalStateException("timeout")

        val reading = service.read(6)

        assertFalse(reading.available)
        assertEquals("NONE", reading.source)
    }

    @Test
    fun `pppoe fixed still reads provisioned simple queue`() {
        every { repository.findById(8) } returns Optional.of(
            subscription(id = 8, ip = "10.64.0.20", pppoeUsername = "gf8", accessMode = AccessMode.PPPOE_FIXED)
        )
        every { mikrotik.printOnDevice(host, "/queue/simple") } returns listOf(
            mapOf(
                "name" to "[stg] id:8, usuario:Fixed",
                "target" to "10.64.0.20/32",
                "rate" to "2000/4000",
                "bytes" to "11/22",
            )
        )

        val reading = service.read(8)

        assertEquals("QUEUE", reading.source)
        assertEquals(4000L, reading.downloadBps)
        assertEquals(2000L, reading.uploadBps)
        verify(exactly = 0) { mikrotik.printOnDevice(host, "/interface") }
    }

    private fun subscription(
        id: Int = 6,
        ip: String? = null,
        pppoeUsername: String? = "gf6",
        accessMode: AccessMode = AccessMode.PPPOE_DYNAMIC,
        hostDevice: NetworkDevice? = host,
    ): Subscription {
        return Subscription(
            id = id,
            equipmentCondition = EquipmentCondition.values().first(),
            ip = ip,
            pppoeUsername = pppoeUsername,
            accessMode = accessMode,
            hostDevice = hostDevice,
        )
    }
}
