package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.repository.ObsIssueRepository
import com.dscorp.wispadmin.observability.repository.ObsSpanRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

class ObsEndpointDetailServiceTest {

    private val metricQueryService = mockk<ObsMetricQueryService>()
    private val databaseQueryService = mockk<ObsDatabaseQueryService>()
    private val spanRepository = mockk<ObsSpanRepository>()
    private val issueRepository = mockk<ObsIssueRepository>()

    private val service = ObsEndpointDetailService(
        metricQueryService,
        databaseQueryService,
        spanRepository,
        issueRepository,
        "America/Lima"
    )

    @Test
    fun `agrega solo la ruta solicitada y calcula percentiles y ratio de db`() {
        val from = LocalDateTime.of(2026, 3, 1, 0, 0, 0)
        val to = LocalDateTime.of(2026, 3, 1, 1, 0, 0)

        every { spanRepository.rootSpanMetricsByRoute(any(), any(), null) } returns listOf(
            arrayOf<Any>("/api/a", "GET", 100L, "OK"),
            arrayOf<Any>("/api/a", "GET", 200L, "OK"),
            arrayOf<Any>("/api/a", "GET", 300L, "ERROR"),
            arrayOf<Any>("/api/a", "GET", 400L, "OK"),
            arrayOf<Any>("/api/b", "POST", 999L, "OK")
        )
        every { spanRepository.aggregateDbTimeByRoute(any(), any(), null) } returns listOf(
            arrayOf<Any>("/api/a", 500L)
        )
        every { metricQueryService.timeSeries(from, to, "/api/a") } returns emptyList()
        every { databaseQueryService.topQueries(any(), any(), any(), null, "/api/a") } returns emptyList()
        every { databaseQueryService.nPlusOne(any(), any(), any(), null, "/api/a") } returns emptyList()
        every { issueRepository.findIssuesByRoute("/api/a", from, to, any()) } returns emptyList()
        every {
            spanRepository.searchRootSpans(any(), any(), "/api/a", any(), any(), any(), any(), null, any<Pageable>())
        } returns PageImpl(emptyList())

        val result = service.detail("/api/a", from, to, null)

        assertEquals("/api/a", result.metrics.route)
        assertEquals(4L, result.metrics.totalRequests)
        assertEquals(1L, result.metrics.totalErrors)
        assertEquals(200L, result.metrics.p50Ms)
        assertEquals(400L, result.metrics.p95Ms)
        assertEquals(400L, result.metrics.maxMs)
        assertEquals(500L, result.metrics.dbTimeMs)
        assertEquals(0.5, result.metrics.dbTimeRatio)
    }
}
