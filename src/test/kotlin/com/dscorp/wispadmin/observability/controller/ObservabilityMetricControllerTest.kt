package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.dto.EndpointDetailDto
import com.dscorp.wispadmin.observability.dto.EndpointDetailMetricsDto
import com.dscorp.wispadmin.observability.service.ObsEndpointDetailService
import com.dscorp.wispadmin.observability.service.ObsMetricQueryService
import com.dscorp.wispadmin.observability.service.ObsRumQueryService
import com.dscorp.wispadmin.observability.service.ObsSystemMetricQueryService
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

class ObservabilityMetricControllerTest {

    private val metricQueryService = mockk<ObsMetricQueryService>()
    private val rumMetricQueryService = mockk<ObsRumQueryService>()
    private val systemMetricQueryService = mockk<ObsSystemMetricQueryService>()
    private val endpointDetailService = mockk<ObsEndpointDetailService>()

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(
            ObservabilityMetricController(
                metricQueryService,
                rumMetricQueryService,
                systemMetricQueryService,
                endpointDetailService
            )
        )
        .build()

    @Test
    fun `propaga el filtro release a web vitals`() {
        every { rumMetricQueryService.aggregate(any(), any(), "1.0.0") } returns emptyList()

        mockMvc.perform(get("/observability/metrics/web-vitals").param("release", "1.0.0"))
            .andExpect(status().isOk)

        verify { rumMetricQueryService.aggregate(any(), any(), "1.0.0") }
    }

    @Test
    fun `el detalle de endpoint devuelve el contrato agregado y propaga los filtros`() {
        val from = LocalDateTime.of(2026, 3, 1, 0, 0, 0)
        val to = LocalDateTime.of(2026, 3, 1, 1, 0, 0)
        every {
            endpointDetailService.detail("/api/subscriptions", from, to, "1.0.0")
        } returns EndpointDetailDto(
            route = "/api/subscriptions",
            release = "1.0.0",
            from = from,
            to = to,
            metrics = EndpointDetailMetricsDto(
                route = "/api/subscriptions",
                httpMethod = "GET",
                totalRequests = 120,
                totalErrors = 3,
                errorRate = 0.025,
                avgMs = 45.0,
                p50Ms = 40,
                p95Ms = 120,
                p99Ms = 180,
                maxMs = 250,
                dbTimeMs = 30,
                dbTimeRatio = 0.5
            ),
            timeSeries = emptyList(),
            topQueries = emptyList(),
            nPlusOne = emptyList(),
            issues = emptyList(),
            sampleTraces = emptyList()
        )

        mockMvc.perform(
            get("/observability/metrics/endpoints/detail")
                .param("route", "/api/subscriptions")
                .param("from", "2026-03-01T00:00:00")
                .param("to", "2026-03-01T01:00:00")
                .param("release", "1.0.0")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.route").value("/api/subscriptions"))
            .andExpect(jsonPath("$.release").value("1.0.0"))
            .andExpect(jsonPath("$.metrics.p50Ms").value(40))
            .andExpect(jsonPath("$.metrics.p95Ms").value(120))
            .andExpect(jsonPath("$.metrics.p99Ms").value(180))
            .andExpect(jsonPath("$.metrics.totalRequests").value(120))
            .andExpect(jsonPath("$.metrics.errorRate").value(0.025))

        verify { endpointDetailService.detail("/api/subscriptions", from, to, "1.0.0") }
    }
}
