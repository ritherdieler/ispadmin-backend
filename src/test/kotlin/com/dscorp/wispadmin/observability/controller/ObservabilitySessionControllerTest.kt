package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.dto.PagedResponse
import com.dscorp.wispadmin.observability.service.ObsSessionQueryService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDateTime

class ObservabilitySessionControllerTest {

    private val sessionQueryService = mockk<ObsSessionQueryService>()

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(ObservabilitySessionController(sessionQueryService))
        .build()

    @Test
    fun `propaga el filtro release al servicio de sesiones`() {
        val from = LocalDateTime.of(2026, 1, 1, 0, 0, 0)
        val to = LocalDateTime.of(2026, 1, 2, 0, 0, 0)
        every {
            sessionQueryService.listSessions(from, to, "2.0.1", 0, 25)
        } returns PagedResponse(emptyList(), 0, 25, 0, 0)

        mockMvc.perform(
            get("/observability/sessions")
                .param("from", "2026-01-01T00:00:00")
                .param("to", "2026-01-02T00:00:00")
                .param("release", "2.0.1")
        ).andExpect(status().isOk)

        verify { sessionQueryService.listSessions(from, to, "2.0.1", 0, 25) }
    }
}
