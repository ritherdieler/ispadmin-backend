package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.SubscriptionLiveReadingDto
import com.dscorp.wispadmin.wispadmin.service.SubscriptionLiveReadingService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SubscriptionLiveReadingControllerTest {

    private val service = mockk<SubscriptionLiveReadingService>()
    private val mockMvc = MockMvcBuilders.standaloneSetup(SubscriptionLiveReadingController(service)).build()

    @Test
    fun `GET live-readings returns 200 with queue contract fields`() {
        every { service.read(7) } returns SubscriptionLiveReadingDto(
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

        mockMvc.get("/subscription/7/live-readings").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.subscriptionId") { value(7) }
            jsonPath("$.available") { value(true) }
            jsonPath("$.pppoe") { doesNotExist() }
            jsonPath("$.timestamp") { value("2026-09-15T17:40:00Z") }
            jsonPath("$.downloadBps") { value(1_800_000) }
            jsonPath("$.uploadBps") { value(145_000) }
            jsonPath("$.rxBytes") { value(2_147_483_648) }
            jsonPath("$.txBytes") { value(152_043_520) }
            jsonPath("$.source") { value("QUEUE") }
        }
    }

    @Test
    fun `GET live-readings returns 200 with pppoe session fields`() {
        every { service.read(6) } returns SubscriptionLiveReadingDto(
            subscriptionId = 6,
            available = true,
            pppoe = "gf6",
            timestamp = "2026-09-15T17:40:00Z",
            downloadBps = 8_500_000,
            uploadBps = 145_000,
            rxBytes = 3_134_442_266,
            txBytes = 851_540_868,
            source = "PPPOE",
        )

        mockMvc.get("/subscription/6/live-readings").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.subscriptionId") { value(6) }
            jsonPath("$.available") { value(true) }
            jsonPath("$.pppoe") { value("gf6") }
            jsonPath("$.timestamp") { value("2026-09-15T17:40:00Z") }
            jsonPath("$.downloadBps") { value(8_500_000) }
            jsonPath("$.uploadBps") { value(145_000) }
            jsonPath("$.rxBytes") { value(3_134_442_266) }
            jsonPath("$.txBytes") { value(851_540_868) }
            jsonPath("$.source") { value("PPPOE") }
        }
    }
}
