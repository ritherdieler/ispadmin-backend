package com.dscorp.wispadmin.traffic.controller

import com.dscorp.wispadmin.traffic.dto.SubscriptionLiveReadingDto
import com.dscorp.wispadmin.traffic.service.LiveReadingIdentity
import com.dscorp.wispadmin.traffic.service.SubscriptionLiveReadingService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class TrafficSubscriptionLiveReadingControllerTest {

    private val service = mockk<SubscriptionLiveReadingService>()
    private val mockMvc = MockMvcBuilders.standaloneSetup(TrafficSubscriptionLiveReadingController(service)).build()

    @Test
    fun `GET live-readings keeps backoffice contract fields`() {
        val identity = slot<LiveReadingIdentity>()
        every { service.read(capture(identity)) } returns SubscriptionLiveReadingDto(
            subscriptionId = 7,
            available = true,
            pppoe = null,
            timestamp = "2026-09-15T17:40:00Z",
            downloadBps = 1_800_000,
            uploadBps = 145_000,
            rxBytes = 2_147_483_648,
            txBytes = 152_043_520,
            source = "QUEUE",
        )

        mockMvc.get("/api/traffic/v1/by-subscription/7/live-readings") {
            param("accessMode", "STATIC_IP")
            param("ip", "192.168.250.16")
            param("hostDeviceId", "8")
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.subscriptionId") { value(7) }
            jsonPath("$.available") { value(true) }
            jsonPath("$.source") { value("QUEUE") }
            jsonPath("$.downloadBps") { value(1_800_000) }
            jsonPath("$.rxBytes") { value(2_147_483_648) }
        }
        assertEquals("STATIC_IP", identity.captured.accessMode)
        assertEquals("192.168.250.16", identity.captured.ip)
        assertEquals(8, identity.captured.hostDeviceId)
    }
}
