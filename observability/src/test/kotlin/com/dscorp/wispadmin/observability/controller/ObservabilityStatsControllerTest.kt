package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.dto.OverviewStatsDto
import com.dscorp.wispadmin.observability.service.ObsQueryService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDateTime

class ObservabilityStatsControllerTest {

    private val queryService = mockk<ObsQueryService>()

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(ObservabilityStatsController(queryService))
        .build()

    @Test
    fun `propaga el rango from y to al overview`() {
        val from = LocalDateTime.of(2026, 2, 1, 0, 0, 0)
        val to = LocalDateTime.of(2026, 2, 8, 0, 0, 0)
        every { queryService.overview(from, to) } returns emptyOverview()

        mockMvc.perform(
            get("/observability/stats/overview")
                .param("from", "2026-02-01T00:00:00")
                .param("to", "2026-02-08T00:00:00")
        ).andExpect(status().isOk)

        verify { queryService.overview(from, to) }
    }

    private fun emptyOverview() = OverviewStatsDto(
        openIssues = 0,
        resolvedIssues = 0,
        ignoredIssues = 0,
        eventsLast24h = 0,
        eventsLastHour = 0,
        issuesByPlatform = emptyMap(),
        openIssuesBySeverity = emptyMap(),
        eventsByFeature = emptyMap(),
        topIssues = emptyList(),
        tracesLastHour = 0,
        errorTraceRate = 0.0,
        p95TraceDurationMs = 0,
        slowTraces = emptyList()
    )
}
