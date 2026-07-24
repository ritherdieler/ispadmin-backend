package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.EndpointMetricAggregateDto
import com.dscorp.wispadmin.observability.entity.ObsDeployEvent
import com.dscorp.wispadmin.observability.repository.ObsDeployEventRepository
import com.dscorp.wispadmin.observability.repository.ObsEventRepository
import com.dscorp.wispadmin.observability.repository.ObsIssueRepository
import com.dscorp.wispadmin.observability.repository.ObsSpanRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ObsReleaseSummaryServiceTest {

    private val deployEventRepository = mockk<ObsDeployEventRepository>()
    private val issueRepository = mockk<ObsIssueRepository>()
    private val eventRepository = mockk<ObsEventRepository>()
    private val spanRepository = mockk<ObsSpanRepository>()
    private val metricQueryService = mockk<ObsMetricQueryService>()
    private val rumMetricQueryService = mockk<ObsRumQueryService>()

    private val service = ObsReleaseSummaryService(
        deployEventRepository,
        issueRepository,
        eventRepository,
        spanRepository,
        metricQueryService,
        rumMetricQueryService,
        "America/Lima"
    )

    @Test
    fun `resuelve version previa, adopcion y detecta regresion de latencia`() {
        val deployedAt = LocalDateTime.of(2026, 4, 1, 10, 0, 0)
        every { deployEventRepository.findFirstByReleaseOrderByDeployedAtDesc("2.3.0") } returns
            ObsDeployEvent(id = 1, platform = "backend", release = "2.3.0", deployedAt = deployedAt)
        every {
            deployEventRepository.findFirstByPlatformAndDeployedAtGreaterThanOrderByDeployedAtAsc("backend", deployedAt)
        } returns null
        every {
            deployEventRepository.findFirstByPlatformAndDeployedAtLessThanOrderByDeployedAtDesc("backend", deployedAt)
        } returns ObsDeployEvent(id = 2, platform = "backend", release = "2.2.0", deployedAt = deployedAt.minusDays(3))

        every { issueRepository.findNewIssuesForRelease("2.3.0", deployedAt, any()) } returns emptyList()
        every { issueRepository.countNewIssuesForRelease("2.3.0", deployedAt) } returns 2

        every { metricQueryService.aggregate(any(), any(), "2.3.0") } returns listOf(
            aggregate("/api/a", 200L)
        )
        every { metricQueryService.aggregate(any(), any(), "2.2.0") } returns listOf(
            aggregate("/api/a", 100L)
        )
        every { rumMetricQueryService.aggregate(any(), any(), "2.3.0") } returns emptyList()

        every { eventRepository.countDistinctSessionsByReleaseBetween("2.3.0", any(), any()) } returns 12
        every { eventRepository.countByReleaseBetween("2.3.0", any(), any()) } returns 340
        every { spanRepository.countRootSpansByReleaseBetween("2.3.0", any(), any()) } returns 88

        val result = service.summary("2.3.0")

        assertEquals("2.2.0", result.previousVersion)
        assertEquals(2L, result.newIssuesCount)
        assertEquals(12L, result.adoption.sessionCount)
        assertEquals(340L, result.adoption.eventCount)
        assertEquals(88L, result.adoption.traceCount)
        val comparison = result.latencyComparison.first { it.route == "/api/a" }
        assertEquals(100.0, comparison.deltaP95Pct)
        assertTrue(comparison.regressed)
    }

    private fun aggregate(route: String, p95: Long) = EndpointMetricAggregateDto(
        route = route,
        httpMethod = "GET",
        totalRequests = 10,
        totalErrors = 0,
        errorRate = 0.0,
        avgMs = p95.toDouble() / 2,
        p95Ms = p95,
        p99Ms = p95,
        maxMs = p95,
        dbTimeMs = null,
        dbTimeRatio = null
    )
}
