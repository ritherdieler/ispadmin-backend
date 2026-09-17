package com.dscorp.wispadmin.wispadmin.service

import com.dscorp.wispadmin.shared.config.GigafiberEnvironmentProperties
import com.dscorp.wispadmin.wispadmin.data.model.AccessMode
import com.dscorp.wispadmin.wispadmin.data.model.EquipmentCondition
import com.dscorp.wispadmin.wispadmin.data.model.NetworkDevice
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.trafficclient.TrafficHttpClient
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class SubscriptionLiveReadingServiceTest {

    private val repository = mockk<SubscriptionRepository>()
    private val trafficHttpClient = mockk<TrafficHttpClient>()
    private val environment = GigafiberEnvironmentProperties()
    private val objectMapper = ObjectMapper()
    private val clock = Clock.fixed(Instant.parse("2026-09-15T17:40:00Z"), ZoneOffset.UTC)
    private val service = SubscriptionLiveReadingService(
        repository,
        trafficHttpClient,
        environment,
        objectMapper,
    ).also { it.clock = clock }
    private val host = NetworkDevice(
        id = 8,
        name = "MK2",
        networkDeviceType = NetworkDevice.NetworkDeviceType.CLOUD_CORE_ROUTER,
    )

    @Test
    fun `read proxies Traffic live-readings and never opens RouterOS`() {
        every { repository.findById(7) } returns Optional.of(
            subscription(id = 7, ip = "192.168.250.16", pppoeUsername = null, accessMode = AccessMode.STATIC_IP),
        )
        every {
            trafficHttpClient.getJson(
                "/api/traffic/v1/by-subscription/7/live-readings",
                match { it.contains("accessMode=STATIC_IP") && it.contains("hostDeviceId=8") && it.contains("ip=192.168.250.16") },
            )
        } returns ResponseEntity.ok(
            """
            {
              "subscriptionId": 7,
              "available": true,
              "pppoe": null,
              "timestamp": "2026-09-15T17:40:00Z",
              "downloadBps": 1800000,
              "uploadBps": 145000,
              "rxBytes": 2147483648,
              "txBytes": 152043520,
              "source": "QUEUE"
            }
            """.trimIndent(),
        )

        val reading = service.read(7)

        assertTrue(reading.available)
        assertEquals(7, reading.subscriptionId)
        assertEquals("QUEUE", reading.source)
        assertEquals(1_800_000L, reading.downloadBps)
        assertEquals(145_000L, reading.uploadBps)
        assertEquals(2_147_483_648L, reading.rxBytes)
        assertEquals(152_043_520L, reading.txBytes)
        verify(exactly = 1) {
            trafficHttpClient.getJson("/api/traffic/v1/by-subscription/7/live-readings", any())
        }
        verify(exactly = 0) { trafficHttpClient.postJson(any()) }
    }

    @Test
    fun `read maps PPPOE contract fields from Traffic JSON`() {
        every { repository.findById(6) } returns Optional.of(subscription())
        every {
            trafficHttpClient.getJson("/api/traffic/v1/by-subscription/6/live-readings", any())
        } returns ResponseEntity.ok(
            """
            {
              "subscriptionId": 6,
              "available": true,
              "pppoe": "gf6",
              "timestamp": "2026-09-15T17:40:00Z",
              "downloadBps": 8500000,
              "uploadBps": 145000,
              "rxBytes": 3134442266,
              "txBytes": 851540868,
              "source": "PPPOE"
            }
            """.trimIndent(),
        )

        val reading = service.read(6)

        assertEquals("PPPOE", reading.source)
        assertEquals("gf6", reading.pppoe)
        assertEquals(8_500_000L, reading.downloadBps)
        assertEquals(3_134_442_266L, reading.rxBytes)
    }

    @Test
    fun `read always sends accessMode on the internal query`() {
        every { repository.findById(6) } returns Optional.of(subscription())
        val query = slot<String>()
        every {
            trafficHttpClient.getJson("/api/traffic/v1/by-subscription/6/live-readings", capture(query))
        } returns ResponseEntity.ok(
            """
            {
              "subscriptionId": 6,
              "available": false,
              "pppoe": null,
              "timestamp": "2026-09-15T17:40:00Z",
              "downloadBps": 0,
              "uploadBps": 0,
              "rxBytes": 0,
              "txBytes": 0,
              "source": "NONE"
            }
            """.trimIndent(),
        )

        service.read(6)

        assertTrue(query.captured.contains("accessMode=PPPOE_DYNAMIC"))
    }

    @Test
    fun `missing subscription returns unavailable none without calling Traffic`() {
        every { repository.findById(6) } returns Optional.empty()

        val reading = service.read(6)

        assertFalse(reading.available)
        assertEquals("NONE", reading.source)
        verify(exactly = 0) { trafficHttpClient.getJson(any(), any()) }
    }

    @Test
    fun `traffic failure returns unavailable none without throwing`() {
        every { repository.findById(6) } returns Optional.of(subscription())
        every {
            trafficHttpClient.getJson("/api/traffic/v1/by-subscription/6/live-readings", any())
        } throws IllegalStateException("timeout")

        val reading = service.read(6)

        assertEquals(6, reading.subscriptionId)
        assertFalse(reading.available)
        assertEquals("NONE", reading.source)
        assertEquals("2026-09-15T17:40:00Z", reading.timestamp)
        assertEquals(0, reading.downloadBps)
    }

    private fun subscription(
        id: Int = 6,
        ip: String? = null,
        pppoeUsername: String? = "gf6",
        accessMode: AccessMode = AccessMode.PPPOE_DYNAMIC,
    ): Subscription {
        return Subscription(
            id = id,
            equipmentCondition = EquipmentCondition.values().first(),
            ip = ip,
            pppoeUsername = pppoeUsername,
            accessMode = accessMode,
            hostDevice = host,
        )
    }
}
