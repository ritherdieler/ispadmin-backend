package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.dto.DbQueryAggregateDto
import com.dscorp.wispadmin.observability.dto.EventDto
import com.dscorp.wispadmin.observability.dto.IssueSummaryDto
import com.dscorp.wispadmin.observability.dto.SpanDto
import com.dscorp.wispadmin.observability.dto.TraceDetailDto
import com.dscorp.wispadmin.observability.entity.ObsIssueStatus
import com.dscorp.wispadmin.observability.repository.ObsAlertEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.LocalDateTime

class ObsLlmContextServiceTest {

    private lateinit var issueQueryService: ObsQueryService
    private lateinit var traceQueryService: ObsTraceQueryService
    private lateinit var sessionQueryService: ObsSessionQueryService
    private lateinit var databaseQueryService: ObsDatabaseQueryService
    private lateinit var alertEventRepository: ObsAlertEventRepository
    private lateinit var service: ObsLlmContextService

    @BeforeEach
    fun setUp() {
        issueQueryService = mock(ObsQueryService::class.java)
        traceQueryService = mock(ObsTraceQueryService::class.java)
        sessionQueryService = mock(ObsSessionQueryService::class.java)
        databaseQueryService = mock(ObsDatabaseQueryService::class.java)
        alertEventRepository = mock(ObsAlertEventRepository::class.java)
        service = ObsLlmContextService(
            issueQueryService,
            traceQueryService,
            sessionQueryService,
            databaseQueryService,
            alertEventRepository,
            ObjectMapper()
        )
    }

    @Test
    fun `buildIssueContext incluye mensaje, stacktrace y encabezados de spans y sql cuando hay trace`() {
        val issueId = 1L
        `when`(issueQueryService.getIssue(issueId)).thenReturn(issueSummary(issueId))
        `when`(issueQueryService.getLatestEvent(issueId)).thenReturn(
            event(
                message = "NullPointerException al procesar pago",
                stacktrace = "java.lang.NullPointerException\n\tat com.example.PaymentService.process(PaymentService.kt:42)",
                correlationId = "trace-1",
                sessionId = "sess-1"
            )
        )
        `when`(traceQueryService.getTrace("trace-1")).thenReturn(
            TraceDetailDto(
                traceId = "trace-1",
                spans = listOf(
                    span(spanId = "s1", parentSpanId = null, name = "POST /payments", durationMs = 120),
                    span(spanId = "s2", parentSpanId = "s1", name = "db.query", durationMs = 90, dbStatement = "SELECT * FROM payments WHERE id = ?")
                ),
                events = emptyList()
            )
        )
        `when`(sessionQueryService.getSession("sess-1")).thenReturn(null)
        `when`(databaseQueryService.topQueries(anyLong(), anyLong(), anyInt(), any(), any())).thenReturn(
            listOf(
                DbQueryAggregateDto(
                    statement = "SELECT * FROM payments WHERE id = ?",
                    calls = 5,
                    totalMs = 400,
                    avgMs = 80.0,
                    maxMs = 120,
                    pct = 0.5
                )
            )
        )

        val content = service.buildIssueContext(issueId)

        assertNotNull(content)
        assertTrue(content!!.contains("NullPointerException al procesar pago"), "debe incluir el mensaje del error")
        assertTrue(content.contains("PaymentService.process"), "debe incluir el stacktrace")
        assertTrue(content.contains("waterfall de spans"), "debe incluir encabezado de spans")
        assertTrue(content.contains("SQL / N+1"), "debe incluir encabezado de SQL")
    }

    @Test
    fun `buildIssueContext no falla cuando el evento no tiene correlationId ni sessionId`() {
        val issueId = 2L
        `when`(issueQueryService.getIssue(issueId)).thenReturn(issueSummary(issueId))
        `when`(issueQueryService.getLatestEvent(issueId)).thenReturn(
            event(
                message = "Error sin traza",
                stacktrace = "java.lang.IllegalStateException",
                correlationId = null,
                sessionId = null
            )
        )

        val content: String? = service.buildIssueContext(issueId)

        assertNotNull(content)
        assertTrue(content!!.contains("Error sin traza"))
    }

    private fun issueSummary(id: Long) = IssueSummaryDto(
        id = id,
        fingerprint = "fp-$id",
        title = "Fallo en pagos",
        platform = "backend",
        severity = "error",
        status = ObsIssueStatus.OPEN,
        errorType = "NullPointerException",
        lastMessage = "NullPointerException",
        lastEnvironment = "production",
        lastRelease = "1.2.3",
        eventCount = 7,
        firstSeen = LocalDateTime.now().minusDays(1),
        lastSeen = LocalDateTime.now(),
        jiraIssueKey = null,
        trackerProvider = null,
        trackerIssueKey = null,
        trackerBrowseUrl = null
    )

    private fun event(
        message: String,
        stacktrace: String,
        correlationId: String?,
        sessionId: String?
    ) = EventDto(
        id = 100L,
        issueId = 1L,
        fingerprint = "fp-1",
        eventType = "error",
        platform = "backend",
        severity = "error",
        feature = "payments",
        action = "process",
        message = message,
        errorType = "NullPointerException",
        stacktrace = stacktrace,
        stacktraceSymbolicated = null,
        symbolicated = false,
        environment = "production",
        release = "1.2.3",
        correlationId = correlationId,
        sessionId = sessionId,
        url = "/payments",
        httpMethod = "POST",
        httpStatus = 500,
        durationMs = 120,
        userAgent = "test-agent",
        user = null,
        device = null,
        breadcrumbs = null,
        tags = null,
        context = null,
        replayId = null,
        format = null,
        eventTimestamp = LocalDateTime.now(),
        createdAt = LocalDateTime.now()
    )

    private fun span(
        spanId: String,
        parentSpanId: String?,
        name: String,
        durationMs: Long,
        dbStatement: String? = null
    ) = SpanDto(
        id = null,
        traceId = "trace-1",
        spanId = spanId,
        parentSpanId = parentSpanId,
        name = name,
        kind = if (dbStatement != null) "CLIENT" else "SERVER",
        platform = "backend",
        sessionId = "sess-1",
        startEpochMs = 1_000L,
        durationMs = durationMs,
        status = "OK",
        httpMethod = if (dbStatement == null) "POST" else null,
        httpRoute = if (dbStatement == null) "/payments" else null,
        httpStatus = if (dbStatement == null) 500 else null,
        dbStatement = dbStatement,
        tags = null,
        environment = "production",
        release = "1.2.3"
    )
}
