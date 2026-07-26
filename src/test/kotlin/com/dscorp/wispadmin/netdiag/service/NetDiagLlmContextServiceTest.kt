package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncidentEvent
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagProbeRun
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagProbeRunRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTrapEventRepository
import com.dscorp.wispadmin.netdiag.exception.IncidentNotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.Optional

class NetDiagLlmContextServiceTest {

    private val incidentRepository = mockk<NetDiagIncidentRepository>()
    private val incidentEventRepository = mockk<NetDiagIncidentEventRepository>()
    private val probeRunRepository = mockk<NetDiagProbeRunRepository>()
    private val trapEventRepository = mockk<NetDiagTrapEventRepository>()
    private val service = NetDiagLlmContextService(
        incidentRepository = incidentRepository,
        incidentEventRepository = incidentEventRepository,
        probeRunRepository = probeRunRepository,
        trapEventRepository = trapEventRepository,
        objectMapper = ObjectMapper()
    )

    @BeforeEach
    fun stubTraps() {
        every { trapEventRepository.findTop20ByTargetIdOrderByReceivedAtDesc(any()) } returns emptyList()
    }

    private val target = NetDiagTarget(id = 1L, name = "MK1", deviceRefId = 7L)
    private val incident = NetDiagIncident(
        id = 42L,
        target = target,
        dedupKey = "LINK_DOWN:1:ether1",
        status = "OPEN",
        severity = "P0",
        title = "Link down: ether1",
        reasonCode = "LINK_DOWN",
        openedAt = Instant.parse("2026-07-26T15:00:00Z")
    )

    @Test
    fun `buildMarkdown incluye incidente timeline y probe`() {
        every { incidentRepository.findById(42L) } returns Optional.of(incident)
        every { incidentEventRepository.findByIncidentIdOrderByCreatedAtDesc(42L) } returns listOf(
            NetDiagIncidentEvent(
                id = 1L,
                incident = incident,
                type = "OPENED",
                payload = "iface=ether1",
                createdAt = Instant.parse("2026-07-26T15:00:00Z")
            )
        )
        every { probeRunRepository.findTopByTargetIdOrderByStartedAtDesc(1L) } returns Optional.of(
            NetDiagProbeRun(
                id = 9L,
                target = target,
                status = "SUCCESS",
                startedAt = Instant.parse("2026-07-26T14:59:00Z"),
                payload = """{"interfaces":[{"name":"ether1","running":false}],"health":[{"name":"voltage","value":"24"}],"netwatch":[{"name":"upstream-http","status":"down"}],"optical":[{"interfaceName":"sfp-sfpplus1","rxPowerDbm":-12.0}]}"""
            )
        )

        val markdown = service.buildMarkdown(42L)

        assertTrue(markdown.contains("Link down: ether1"))
        assertTrue(markdown.contains("OPENED"))
        assertTrue(markdown.contains("ether1"))
        assertTrue(markdown.contains("Causa raíz probable"))
        assertTrue(markdown.contains("Netwatch snapshot"))
        assertTrue(markdown.contains("upstream-http"))
        assertTrue(markdown.contains("Optical snapshot"))
        assertTrue(markdown.contains("Health snapshot"))
    }

    @Test
    fun `buildDiagnosticJson retorna mapa estructurado`() {
        every { incidentRepository.findById(42L) } returns Optional.of(incident)
        every { incidentEventRepository.findByIncidentIdOrderByCreatedAtDesc(42L) } returns emptyList()
        every { probeRunRepository.findTopByTargetIdOrderByStartedAtDesc(1L) } returns Optional.of(
            NetDiagProbeRun(
                id = 9L,
                target = target,
                status = "SUCCESS",
                startedAt = Instant.parse("2026-07-26T14:59:00Z"),
                payload = """{"health":[{"name":"voltage","value":"24"}],"netwatch":[{"name":"upstream-http","status":"up"}],"optical":[]}"""
            )
        )

        val json = service.buildDiagnosticJson(42L)

        assertEquals(42L, json["incidentId"])
        assertEquals("LINK_DOWN", json["reasonCode"])
        assertEquals("OPEN", json["status"])
        assertTrue(json.containsKey("netwatchSnapshot"))
        assertTrue(json.containsKey("opticalSnapshot"))
        assertTrue(json.containsKey("healthSnapshot"))
    }

    @Test
    fun `incidente inexistente lanza IncidentNotFoundException`() {
        every { incidentRepository.findById(99L) } returns Optional.empty()
        assertThrows<IncidentNotFoundException> { service.buildMarkdown(99L) }
    }
}
