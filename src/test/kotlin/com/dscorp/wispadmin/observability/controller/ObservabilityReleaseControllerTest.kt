package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.dto.ReleaseAdoptionDto
import com.dscorp.wispadmin.observability.dto.ReleaseRouteLatencyDto
import com.dscorp.wispadmin.observability.dto.ReleaseSummaryDto
import com.dscorp.wispadmin.observability.service.ObsDeployEventService
import com.dscorp.wispadmin.observability.service.ObsReleaseSummaryService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDateTime

class ObservabilityReleaseControllerTest {

    private val deployEventService = mockk<ObsDeployEventService>(relaxed = true)
    private val releaseSummaryService = mockk<ObsReleaseSummaryService>()

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(ObservabilityReleaseController(deployEventService, releaseSummaryService))
        .build()

    @Test
    fun `el summary de release devuelve el contrato agregado`() {
        every { releaseSummaryService.summary("2.3.0") } returns ReleaseSummaryDto(
            version = "2.3.0",
            previousVersion = "2.2.0",
            platform = "backend",
            from = LocalDateTime.of(2026, 4, 1, 0, 0, 0),
            to = LocalDateTime.of(2026, 4, 2, 0, 0, 0),
            newIssues = emptyList(),
            newIssuesCount = 4,
            latencyComparison = listOf(
                ReleaseRouteLatencyDto(
                    route = "/api/subscriptions",
                    httpMethod = "GET",
                    baseAvgMs = 30.0,
                    targetAvgMs = 45.0,
                    baseP95Ms = 80,
                    targetP95Ms = 120,
                    deltaP95Pct = 50.0,
                    regressed = true
                )
            ),
            webVitals = emptyList(),
            adoption = ReleaseAdoptionDto(sessionCount = 12, eventCount = 340, traceCount = 88)
        )

        mockMvc.perform(get("/observability/releases/2.3.0/summary"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.version").value("2.3.0"))
            .andExpect(jsonPath("$.previousVersion").value("2.2.0"))
            .andExpect(jsonPath("$.newIssuesCount").value(4))
            .andExpect(jsonPath("$.latencyComparison[0].regressed").value(true))
            .andExpect(jsonPath("$.latencyComparison[0].deltaP95Pct").value(50.0))
            .andExpect(jsonPath("$.adoption.sessionCount").value(12))
            .andExpect(jsonPath("$.adoption.eventCount").value(340))

        verify { releaseSummaryService.summary("2.3.0") }
    }
}
