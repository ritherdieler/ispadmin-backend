package com.dscorp.wispadmin.netdiag.controller

import com.dscorp.wispadmin.netdiag.dto.TrapIngestResponseDto
import com.dscorp.wispadmin.netdiag.exception.NetDiagExceptionHandler
import com.dscorp.wispadmin.netdiag.service.AlertEvaluator
import com.dscorp.wispadmin.netdiag.service.AlertSignalExtractor
import com.dscorp.wispadmin.netdiag.service.NetDiagIncidentQueryService
import com.dscorp.wispadmin.netdiag.service.NetDiagLlmContextService
import com.dscorp.wispadmin.netdiag.service.NetDiagSnmpTrapIngestService
import com.dscorp.wispadmin.netdiag.service.SyslogIngestAdapter
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class NetDiagTrapIngestControllerTest {

    private val incidentQueryService = mockk<NetDiagIncidentQueryService>()
    private val llmContextService = mockk<NetDiagLlmContextService>()
    private val alertEvaluator = mockk<AlertEvaluator>()
    private val signalExtractor = mockk<AlertSignalExtractor>()
    private val trapIngestService = mockk<NetDiagSnmpTrapIngestService>()
    private val syslogIngestAdapter = mockk<SyslogIngestAdapter>()
    private val objectMapper = ObjectMapper()

    private val mockMvc = MockMvcBuilders
        .standaloneSetup(
            NetDiagController(
                incidentQueryService,
                llmContextService,
                alertEvaluator,
                signalExtractor,
                trapIngestService,
                syslogIngestAdapter
            )
        )
        .setControllerAdvice(NetDiagExceptionHandler())
        .build()

    @Test
    fun `POST traps ingest retorna decisions DTO`() {
        every { trapIngestService.ingest(any()) } returns TrapIngestResponseDto(
            decisions = listOf("OPEN"),
            openedIncidentIds = listOf(88L),
            suppressed = false,
            trapEventId = 3L,
            reasonCode = "SNMP_TRAP_LINK_DOWN"
        )

        mockMvc.perform(
            post("/api/netdiag/traps/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "targetId" to 7,
                            "trapType" to "interfaces",
                            "sourceHost" to "38.224.231.2",
                            "component" to "ether1"
                        )
                    )
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.decisions[0]").value("OPEN"))
            .andExpect(jsonPath("$.trapEventId").value(3))
            .andExpect(jsonPath("$.reasonCode").value("SNMP_TRAP_LINK_DOWN"))

        verify { trapIngestService.ingest(any()) }
    }

    @Test
    fun `POST syslog ingest retorna decisions DTO`() {
        every {
            syslogIngestAdapter.ingest(7L, "bridge loop-protect: interface ether5 disabled")
        } returns com.dscorp.wispadmin.netdiag.service.AlertEvaluationResult(
            decisions = listOf("OPEN"),
            openedIncidentIds = listOf(11L)
        )

        mockMvc.perform(
            post("/api/netdiag/syslog/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "targetId" to 7,
                            "message" to "bridge loop-protect: interface ether5 disabled"
                        )
                    )
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.decisions[0]").value("OPEN"))
            .andExpect(jsonPath("$.openedIncidentIds[0]").value(11))
    }
}
