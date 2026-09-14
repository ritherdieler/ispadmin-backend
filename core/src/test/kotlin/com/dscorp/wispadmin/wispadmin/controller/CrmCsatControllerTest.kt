package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.dto.CsatSummaryDto
import com.dscorp.wispadmin.wispadmin.service.whatsapp.CsatSurveyService
import com.dscorp.wispadmin.wispadmin.service.whatsapp.WhatsAppAuditService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

class CrmCsatControllerTest {

    private val csatSurveyService = mockk<CsatSurveyService>()
    private val auditService = mockk<WhatsAppAuditService>(relaxed = true)
    private lateinit var controller: CrmCsatController

    @BeforeEach
    fun setUp() {
        controller = CrmCsatController(csatSurveyService, auditService)
    }

    @Test
    fun `summary returns dto`() {
        val summary = CsatSummaryDto(
            from = LocalDateTime.of(2026, 7, 1, 0, 0),
            to = LocalDateTime.of(2026, 8, 1, 0, 0),
            scheduled = 0,
            sent = 1,
            answered = 2,
            expired = 0,
            failed = 0,
            responseRate = 0.67,
            averageScore = 4.5,
            byTechnician = emptyList(),
            byPlace = emptyList(),
            byCategory = emptyList(),
            evolution = emptyList(),
            reasons = emptyList()
        )
        every { csatSurveyService.buildSummary(any(), any()) } returns summary

        val response = controller.summary(null, null)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(summary, response.body)
    }
}
