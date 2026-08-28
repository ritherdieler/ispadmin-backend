package com.dscorp.wispadmin.traffic.controller

import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficPollResultDto
import com.dscorp.wispadmin.traffic.service.SubscriptionTrafficPollService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SubscriptionTrafficPollControllerTest {

    private val pollService = mockk<SubscriptionTrafficPollService>()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(SubscriptionTrafficPollController(pollService))
        .build()

    @Test
    fun `POST traffic poll dispara recoleccion desde mikrotik`() {
        every { pollService.pollTraffic() } returns SubscriptionTrafficPollResultDto(
            devicesPolled = 1,
            subscriptionsMatched = 10,
            samplesWritten = 8,
            durationMs = 1200
        )

        mockMvc.post("/traffic/poll").andExpect {
            status { isOk() }
            jsonPath("$.devicesPolled") { value(1) }
            jsonPath("$.samplesWritten") { value(8) }
        }

        verify(exactly = 1) { pollService.pollTraffic() }
    }
}
