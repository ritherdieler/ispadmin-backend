package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
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
    private val service = SubscriptionLiveReadingService(repository, mikrotik).also { it.clock = clock }
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
    fun `queue matched by ip returns queue rates`() {
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
    }

    @Test
    fun `queue matched by pppoe name returns queue rates for gf6`() {
        every { repository.findById(6) } returns Optional.of(subscription())
        every { mikrotik.printOnDevice(host, "/queue/simple") } returns listOf(
            mapOf(
                "name" to "<pppoe-gf6>",
                "target" to "10.11.100.20/32",
                "rate" to "145000/1800000",
                "bytes" to "152043520/2147483648",
            )
        )

        val reading = service.read(6)

        assertTrue(reading.available)
        assertEquals("QUEUE", reading.source)
        assertNull(reading.pppoe)
        assertEquals(1_800_000L, reading.downloadBps)
        assertEquals(145_000L, reading.uploadBps)
    }

    @Test
    fun `queue matched by subscription id in name`() {
        every { repository.findById(6) } returns Optional.of(subscription())
        every { mikrotik.printOnDevice(host, "/queue/simple") } returns listOf(
            mapOf(
                "name" to "id:6, usuario:CLIENTE EEEFIBER",
                "target" to "10.11.100.20/32",
                "rate" to "1000/2000",
                "bytes" to "10/20",
            )
        )

        val reading = service.read(6)

        assertEquals("QUEUE", reading.source)
        assertEquals(2000L, reading.downloadBps)
        assertEquals(1000L, reading.uploadBps)
        assertEquals(20L, reading.rxBytes)
        assertEquals(10L, reading.txBytes)
    }

    @Test
    fun `missing queue falls back to pppoe-in interface`() {
        every { repository.findById(6) } returns Optional.of(subscription())
        every { mikrotik.printOnDevice(host, "/queue/simple") } returns emptyList()
        every { mikrotik.printOnDevice(host, "/interface") } returns listOf(
            mapOf("name" to "ether1", "type" to "ether", "rx-byte" to "9", "tx-byte" to "3"),
            mapOf("name" to "<pppoe-gf6>", "type" to "pppoe-in", "rx-byte" to "2147483648", "tx-byte" to "152043520"),
        )

        val reading = service.read(6)

        assertTrue(reading.available)
        assertEquals("PPPOE", reading.source)
        assertEquals("gf6", reading.pppoe)
        assertEquals(0L, reading.downloadBps)
        assertEquals(0L, reading.uploadBps)
        assertEquals(2_147_483_648L, reading.rxBytes)
        assertEquals(152_043_520L, reading.txBytes)
    }

    @Test
    fun `pppoe-in name without brackets still matches`() {
        every { repository.findById(6) } returns Optional.of(subscription())
        every { mikrotik.printOnDevice(host, "/queue/simple") } returns emptyList()
        every { mikrotik.printOnDevice(host, "/interface") } returns listOf(
            mapOf("name" to "pppoe-gf6", "type" to "pppoe-in", "rx-byte" to "8", "tx-byte" to "2"),
        )

        val reading = service.read(6)

        assertEquals("PPPOE", reading.source)
        assertEquals("gf6", reading.pppoe)
        assertEquals(8L, reading.rxBytes)
        assertEquals(2L, reading.txBytes)
    }

    @Test
    fun `no queue and no pppoe session returns none`() {
        every { repository.findById(6) } returns Optional.of(subscription())
        every { mikrotik.printOnDevice(host, "/queue/simple") } returns emptyList()
        every { mikrotik.printOnDevice(host, "/interface") } returns listOf(
            mapOf("name" to "ether1", "type" to "ether", "rx-byte" to "9", "tx-byte" to "3"),
        )

        val reading = service.read(6)

        assertFalse(reading.available)
        assertEquals("NONE", reading.source)
        assertNull(reading.pppoe)
    }

    @Test
    fun `mikrotik error returns unavailable none`() {
        every { repository.findById(6) } returns Optional.of(subscription())
        every { mikrotik.printOnDevice(host, "/queue/simple") } throws IllegalStateException("timeout")

        val reading = service.read(6)

        assertFalse(reading.available)
        assertEquals("NONE", reading.source)
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
