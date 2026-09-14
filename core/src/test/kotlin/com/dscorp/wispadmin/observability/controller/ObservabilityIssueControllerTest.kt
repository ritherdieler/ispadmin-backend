package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.dto.PagedResponse
import com.dscorp.wispadmin.observability.service.ObsQueryService
import com.dscorp.wispadmin.observability.service.ObsTicketApplicationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ObservabilityIssueControllerTest {

    private val queryService = mockk<ObsQueryService>()
    private val ticketApplicationService = mockk<ObsTicketApplicationService>(relaxed = true)

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(ObservabilityIssueController(queryService, ticketApplicationService))
        .build()

    @Test
    fun `propaga el filtro release al servicio de issues`() {
        every {
            queryService.searchIssues(any(), any(), any(), any(), "1.4.0", any(), any(), any(), any(), any())
        } returns PagedResponse(emptyList(), 0, 25, 0, 0)

        mockMvc.perform(get("/observability/issues").param("release", "1.4.0"))
            .andExpect(status().isOk)

        verify {
            queryService.searchIssues(null, null, null, null, "1.4.0", null, null, null, 0, 25)
        }
    }
}
