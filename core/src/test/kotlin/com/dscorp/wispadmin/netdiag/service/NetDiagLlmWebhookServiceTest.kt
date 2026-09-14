package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncidentEvent
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.time.Instant

class NetDiagLlmWebhookServiceTest {

    private val llmContextService = mockk<NetDiagLlmContextService>()
    private val incidentEventRepository = mockk<NetDiagIncidentEventRepository>()
    private val restTemplate = mockk<RestTemplate>()
    private val properties = NetDiagProperties().apply {
        llm.webhookEnabled = true
        llm.webhookUrl = "https://agent.example/hook"
    }

    private lateinit var service: NetDiagLlmWebhookService

    @BeforeEach
    fun setUp() {
        every { incidentEventRepository.save(any()) } answers { firstArg() }
        service = NetDiagLlmWebhookService(
            properties = properties,
            llmContextService = llmContextService,
            incidentEventRepository = incidentEventRepository,
            restTemplate = restTemplate
        )
    }

    @Test
    fun `notifyIncidentOpened envia diagnostic-json a webhook para P0`() {
        val incident = NetDiagIncident(
            id = 42L,
            dedupKey = "LINK_DOWN:1",
            status = "OPEN",
            severity = "P0",
            title = "WAN down",
            openedAt = Instant.now()
        )
        val payload = mapOf("incidentId" to 42L, "severity" to "P0")
        every { llmContextService.buildDiagnosticJson(42L) } returns payload

        val entitySlot = slot<HttpEntity<Map<String, Any?>>>()
        every {
            restTemplate.exchange(
                "https://agent.example/hook",
                HttpMethod.POST,
                capture(entitySlot),
                String::class.java
            )
        } returns ResponseEntity.ok("accepted")

        service.notifyIncidentOpened(incident)

        assertEquals(payload, entitySlot.captured.body)
        verify { incidentEventRepository.save(match { it.type == "LLM_WEBHOOK_SENT" && it.incident.id == 42L }) }
    }

    @Test
    fun `notifyIncidentOpened no envia si webhook deshabilitado`() {
        properties.llm.webhookEnabled = false
        val incident = NetDiagIncident(id = 1L, severity = "P0", title = "x")

        service.notifyIncidentOpened(incident)

        verify(exactly = 0) { restTemplate.exchange(any<String>(), any(), any(), String::class.java) }
    }
}
