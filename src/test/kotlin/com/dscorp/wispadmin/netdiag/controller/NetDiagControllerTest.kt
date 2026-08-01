package com.dscorp.wispadmin.netdiag.controller

import com.dscorp.wispadmin.netdiag.dto.IncidentDetailDto
import com.dscorp.wispadmin.netdiag.dto.IncidentSummaryDto
import com.dscorp.wispadmin.netdiag.dto.OltLogEventDto
import com.dscorp.wispadmin.netdiag.dto.OltLogPageDto
import com.dscorp.wispadmin.netdiag.exception.IncidentNotFoundException
import com.dscorp.wispadmin.netdiag.exception.NetDiagExceptionHandler
import com.dscorp.wispadmin.netdiag.service.AlertEvaluator
import com.dscorp.wispadmin.netdiag.service.AlertEvaluationResult
import com.dscorp.wispadmin.netdiag.service.AlertSignal
import com.dscorp.wispadmin.netdiag.service.AlertSignalExtractor
import com.dscorp.wispadmin.netdiag.dto.MaintenanceWindowDto
import com.dscorp.wispadmin.netdiag.service.NetDiagMaintenanceService
import com.dscorp.wispadmin.netdiag.service.NetDiagIncidentQueryService
import com.dscorp.wispadmin.netdiag.service.NetDiagLlmContextService
import com.dscorp.wispadmin.netdiag.service.NetDiagOltLogQueryService
import com.dscorp.wispadmin.netdiag.service.NetDiagSnmpTrapIngestService
import com.dscorp.wispadmin.netdiag.service.SyslogIngestAdapter
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class NetDiagControllerTest {

    private val incidentQueryService = mockk<NetDiagIncidentQueryService>()
    private val llmContextService = mockk<NetDiagLlmContextService>()
    private val maintenanceService = mockk<NetDiagMaintenanceService>()
    private val alertEvaluator = mockk<AlertEvaluator>()
    private val signalExtractor = mockk<AlertSignalExtractor>()
    private val trapIngestService = mockk<NetDiagSnmpTrapIngestService>()
    private val syslogIngestAdapter = mockk<SyslogIngestAdapter>()
    private val oltLogQueryService = mockk<NetDiagOltLogQueryService>()
    private val objectMapper = ObjectMapper()

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(
            NetDiagController(
                incidentQueryService,
                llmContextService,
                maintenanceService,
                alertEvaluator,
                signalExtractor,
                trapIngestService,
                syslogIngestAdapter,
                oltLogQueryService
            )
        )
        .setControllerAdvice(NetDiagExceptionHandler())
        .build()

    @Test
    fun `health retorna estado del modulo`() {
        mockMvc.perform(get("/api/netdiag/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.module").value("netdiag"))
    }

    @Test
    fun `lista de incidentes aplica filtros de query`() {
        every {
            incidentQueryService.listIncidents("P0", "OPEN", 7L, "2026-07-01", "2026-07-26")
        } returns listOf(
            IncidentSummaryDto(
                id = 1L,
                targetId = 7L,
                targetName = "MK1",
                dedupKey = "LINK_DOWN:7:ether1",
                status = "OPEN",
                severity = "P0",
                title = "Link down",
                reasonCode = "LINK_DOWN",
                openedAt = Instant.parse("2026-07-26T12:00:00Z"),
                lastNotifiedAt = null
            )
        )

        mockMvc.perform(
            get("/api/netdiag/incidents")
                .param("severity", "P0")
                .param("status", "OPEN")
                .param("targetId", "7")
                .param("dateFrom", "2026-07-01")
                .param("dateTo", "2026-07-26")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].targetName").value("MK1"))
            .andExpect(jsonPath("$[0].severity").value("P0"))
    }

    @Test
    fun `detalle de incidente inexistente retorna 404`() {
        every { incidentQueryService.getIncident(99L) } throws IncidentNotFoundException("Incident not found: 99")

        mockMvc.perform(get("/api/netdiag/incidents/99"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("incident_not_found"))
    }

    @Test
    fun `llm-context retorna markdown texto plano`() {
        every { llmContextService.buildMarkdown(5L) } returns "# Contexto\n"

        mockMvc.perform(get("/api/netdiag/incidents/5/llm-context"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
            .andExpect(content().string("# Contexto\n"))
    }

    @Test
    fun `diagnostic-json retorna mapa en raiz`() {
        every { llmContextService.buildDiagnosticJson(5L) } returns mapOf("incidentId" to 5L)

        mockMvc.perform(get("/api/netdiag/incidents/5/diagnostic-json"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.incidentId").value(5))
    }

    @Test
    fun `ack retorna detalle actualizado`() {
        every { incidentQueryService.acknowledge(5L) } returns detail(status = "ACKNOWLEDGED")

        mockMvc.perform(post("/api/netdiag/incidents/5/ack"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
            .andExpect(jsonPath("$.id").value(5))
    }

    @Test
    fun `resolve retorna detalle actualizado`() {
        every { incidentQueryService.resolve(5L) } returns detail(status = "RESOLVED")

        mockMvc.perform(post("/api/netdiag/incidents/5/resolve"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("RESOLVED"))
    }

    @Test
    fun `silence retorna detalle con estado SILENCED`() {
        every { incidentQueryService.silence(5L, any()) } returns detail(status = "SILENCED")

        mockMvc.perform(
            post("/api/netdiag/incidents/5/silence")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("durationMinutes" to 60)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("SILENCED"))

        verify { incidentQueryService.silence(5L, any()) }
    }

    @Test
    fun `maintenance windows lista ventanas`() {
        every { maintenanceService.list() } returns listOf(
            MaintenanceWindowDto(
                id = 1L,
                targetId = 7L,
                title = "Upgrade MK1",
                description = null,
                startsAt = Instant.parse("2026-07-26T10:00:00Z"),
                endsAt = Instant.parse("2026-07-26T12:00:00Z"),
                suppressNotifications = true,
                createdAt = Instant.parse("2026-07-26T09:00:00Z")
            )
        )

        mockMvc.perform(get("/api/netdiag/maintenance-windows"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].title").value("Upgrade MK1"))
    }

    @Test
    fun `lista logs OLT aplica filtros y pagina`() {
        every {
            oltLogQueryService.listLogs(1, 8, true, "2026-07-01T00:00:00Z", "2026-07-31T23:59:59Z", 0, 20)
        } returns OltLogPageDto(
            items = listOf(
                OltLogEventDto(
                    id = 7L,
                    receivedAt = Instant.parse("2026-07-31T17:00:00Z"),
                    sourceIp = "10.11.104.2",
                    reasonCode = "OLT_ALARM_UNPARSED",
                    board = 1,
                    port = 8,
                    onuIndex = null,
                    targetId = null,
                    severity = null,
                    incidentId = null,
                    channel = "cli_alarm_active",
                    alarmIdHex = null,
                    alarmName = null,
                    component = null,
                    isClear = false,
                    isUnparsed = true,
                    rawMessage = "raw-block"
                )
            ),
            page = 0,
            size = 20,
            totalElements = 1,
            totalPages = 1
        )

        mockMvc.perform(
            get("/api/netdiag/olt/logs")
                .param("board", "1")
                .param("port", "8")
                .param("unparsedOnly", "true")
                .param("dateFrom", "2026-07-01T00:00:00Z")
                .param("dateTo", "2026-07-31T23:59:59Z")
                .param("page", "0")
                .param("size", "20")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].reasonCode").value("OLT_ALARM_UNPARSED"))
            .andExpect(jsonPath("$.items[0].isUnparsed").value(true))
            .andExpect(jsonPath("$.items[0].rawMessage").value("raw-block"))
    }

    @Test
    fun `ingest PON_DOWN evalua alerta`() {
        val signal = AlertSignal(
            reasonCode = "PON_DOWN",
            severity = "P0",
            title = "PON down gpon-0/1",
            dedupKey = "PON_DOWN:2:gpon-0/1"
        )
        every {
            signalExtractor.fromIngest(2L, "PON_DOWN", "P0", "PON down gpon-0/1", "gpon-0/1", "slot=0")
        } returns signal
        every { alertEvaluator.evaluateIngest(2L, listOf(signal)) } returns AlertEvaluationResult(
            decisions = listOf("OPEN"),
            openedIncidentIds = listOf(77L),
            suppressed = false
        )

        mockMvc.perform(
            post("/api/netdiag/alerts/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "targetId" to 2,
                            "reasonCode" to "PON_DOWN",
                            "severity" to "P0",
                            "title" to "PON down gpon-0/1",
                            "component" to "gpon-0/1",
                            "details" to "slot=0"
                        )
                    )
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.decisions[0]").value("OPEN"))
            .andExpect(jsonPath("$.openedIncidentIds[0]").value(77))

        verify { alertEvaluator.evaluateIngest(2L, listOf(signal)) }
    }

    private fun detail(status: String): IncidentDetailDto {
        return IncidentDetailDto(
            id = 5L,
            targetId = 7L,
            targetName = "MK1",
            dedupKey = "LINK_DOWN:7:ether1",
            status = status,
            severity = "P0",
            title = "Link down",
            reasonCode = "LINK_DOWN",
            openedAt = Instant.parse("2026-07-26T12:00:00Z"),
            acknowledgedAt = Instant.parse("2026-07-26T12:05:00Z"),
            resolvedAt = if (status == "RESOLVED") Instant.parse("2026-07-26T12:10:00Z") else null,
            lastNotifiedAt = null,
            events = emptyList()
        )
    }
}
