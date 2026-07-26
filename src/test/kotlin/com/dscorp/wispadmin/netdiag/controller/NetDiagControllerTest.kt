package com.dscorp.wispadmin.netdiag.controller

import com.dscorp.wispadmin.netdiag.exception.IncidentNotFoundException
import com.dscorp.wispadmin.netdiag.exception.NetDiagExceptionHandler
import com.dscorp.wispadmin.netdiag.service.NetDiagIncidentQueryService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class NetDiagControllerTest {

    private val incidentQueryService = mockk<NetDiagIncidentQueryService>()

    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(NetDiagController(incidentQueryService))
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
    fun `lista de incidentes retorna vacia en scaffold`() {
        every { incidentQueryService.listIncidents() } returns emptyList()

        mockMvc.perform(get("/api/netdiag/incidents"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `detalle de incidente inexistente retorna 404`() {
        every { incidentQueryService.getIncident(99L) } throws IncidentNotFoundException("Incident not found: 99")

        mockMvc.perform(get("/api/netdiag/incidents/99"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("incident_not_found"))
    }
}
