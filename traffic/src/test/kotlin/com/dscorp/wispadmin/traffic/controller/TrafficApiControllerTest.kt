package com.dscorp.wispadmin.traffic.controller

import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficLatestDto
import com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficSeriesDto
import com.dscorp.wispadmin.traffic.repository.TrafficAnomalyEventRepository
import com.dscorp.wispadmin.traffic.repository.TrafficSourceRunRepository
import com.dscorp.wispadmin.traffic.service.BandwidthIntelligenceService
import com.dscorp.wispadmin.traffic.service.NetworkTrafficAnalyticsService
import com.dscorp.wispadmin.traffic.service.SubscriptionTrafficPollService
import com.dscorp.wispadmin.traffic.service.SubscriptionTrafficQueryService
import com.dscorp.wispadmin.traffic.service.TrafficAggregationJobService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class TrafficApiControllerTest {

    private val queryService = mockk<SubscriptionTrafficQueryService>()
    private val bandwidthService = mockk<BandwidthIntelligenceService>(relaxed = true)
    private val networkAnalytics = mockk<NetworkTrafficAnalyticsService>(relaxed = true)
    private val pollService = mockk<SubscriptionTrafficPollService>(relaxed = true)
    private val aggregationJobService = mockk<TrafficAggregationJobService>(relaxed = true)
    private val sourceRunRepository = mockk<TrafficSourceRunRepository>(relaxed = true)
    private val anomalyRepository = mockk<TrafficAnomalyEventRepository>(relaxed = true)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(
        TrafficApiController(
            queryService,
            bandwidthService,
            networkAnalytics,
            pollService,
            aggregationJobService,
            sourceRunRepository,
            anomalyRepository,
            TrafficProperties(),
        )
    ).build()

    @Test
    fun `GET by-subscription latest incluye ip y sampleStatus`() {
        every { queryService.getLatest(2360) } returns SubscriptionTrafficLatestDto(
            subscriptionId = 2360,
            bucketStart = "2026-09-03T10:00",
            rxBytes = 100,
            txBytes = 20,
            avgMbpsDown = 8.0,
            avgMbpsUp = 1.0,
            polledAt = "2026-09-03T10:00",
            ip = "192.168.250.20",
            clientIp = "192.168.250.20",
            sampleStatus = "OK",
        )

        mockMvc.get("/api/traffic/v1/by-subscription/2360/latest").andExpect {
            status { isOk() }
            jsonPath("$.subscriptionId") { value(2360) }
            jsonPath("$.ip") { value("192.168.250.20") }
            jsonPath("$.sampleStatus") { value("OK") }
        }
    }

    @Test
    fun `GET by-subscription summary never returns empty body`() {
        every { queryService.getSummary(2360, null) } returns com.dscorp.wispadmin.traffic.dto.SubscriptionTrafficSummaryDto(
            subscriptionId = 2360,
            yearMonth = "2026-09",
            rxBytesTotal = 0,
            txBytesTotal = 0,
            rxGbTotal = 0.0,
            txGbTotal = 0.0,
            maxMbpsDown = 0.0,
            maxMbpsUp = 0.0,
            p95MbpsDown = 0.0,
            p95MbpsUp = 0.0,
            activeDays = 0,
        )

        mockMvc.get("/api/traffic/v1/by-subscription/2360/summary").andExpect {
            status { isOk() }
            jsonPath("$.subscriptionId") { value(2360) }
            jsonPath("$.rxGbTotal") { value(0.0) }
        }
    }

    @Test
    fun `GET by-ip series no usa subscriptionId como clave de recoleccion`() {
        every { queryService.getSeriesByIp("192.168.250.20", "sample", null, null) } returns SubscriptionTrafficSeriesDto(
            subscriptionId = 2360,
            granularity = "sample",
            points = emptyList(),
            clientIp = "192.168.250.20",
        )

        mockMvc.get("/api/traffic/v1/by-ip/192.168.250.20/series").andExpect {
            status { isOk() }
            jsonPath("$.clientIp") { value("192.168.250.20") }
        }
    }
}
