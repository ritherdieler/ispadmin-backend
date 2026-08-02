package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.CrmMetricsSummaryDto
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CrmMetricsService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

class CrmMetricsControllerTest {

    private val metricsService = mockk<CrmMetricsService>()
    private lateinit var controller: CrmMetricsController

    @BeforeEach
    fun setUp() {
        controller = CrmMetricsController(metricsService)
    }

    @Test
    fun `summary returns dto`() {
        val summary = CrmMetricsSummaryDto(
            from = LocalDateTime.of(2026, 7, 1, 0, 0),
            to = LocalDateTime.of(2026, 8, 1, 0, 0),
            resolvedConversations = 1,
            pendingUnassigned = 2,
            assignedActive = 1,
            reopenEvents = 0,
            transferEvents = 0,
            avgFirstResponseMinutes = 5.0,
            avgResolutionMinutes = 60.0,
            firstResponseWithinSlaRate = 1.0,
            resolutionWithinSlaRate = 1.0,
            abandonmentCount = 0,
            botToHumanTransfers = 1,
            autoResolutionRate = 0.0,
            ticketsFromConversations = 1,
            linkedTicketsSlaBreached = 0,
            recurringCustomerPhones = 0,
            contactReasons = emptyList(),
            byAgent = emptyList(),
            byPlace = emptyList(),
            byCategory = emptyList(),
            csatReference = com.dscorp.wispadmin.wispadmin.dto.CrmMetricsCsatReferenceDto(
                panelPath = "/crm/csat",
                responseRate = 0.5,
                averageScore = 4.0,
                answeredSurveys = 1
            )
        )
        every { metricsService.buildSummary(any(), any()) } returns summary

        val response = controller.summary(null, null)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(summary, response.body)
    }
}
