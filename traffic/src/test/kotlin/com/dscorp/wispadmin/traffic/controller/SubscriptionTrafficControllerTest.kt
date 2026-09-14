package com.dscorp.wispadmin.traffic.controller

import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficDayDto
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficDayTotalsDto
import com.dscorp.wispadmin.traffic.service.SubscriptionTrafficQueryService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDate

class SubscriptionTrafficControllerTest {

    private val queryService = mockk<SubscriptionTrafficQueryService>()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(SubscriptionTrafficController(queryService))
        .build()

    @Test
    fun `GET traffic day acepta date ISO yyyy-MM-dd`() {
        val day = LocalDate.of(2026, 8, 26)
        every { queryService.getDay(2328, day) } returns SubscriptionTrafficDayDto(
            subscriptionId = 2328,
            date = "2026-08-26",
            points = emptyList(),
            totals = SubscriptionTrafficDayTotalsDto(rxBytes = 0, txBytes = 0),
            peakHour = null,
            peakHourLabel = null
        )

        mockMvc.get("/subscription/2328/traffic/day") {
            param("date", "2026-08-26")
        }.andExpect {
            status { isOk() }
            jsonPath("$.subscriptionId") { value(2328) }
            jsonPath("$.date") { value("2026-08-26") }
        }

        verify(exactly = 1) { queryService.getDay(2328, day) }
    }
}
