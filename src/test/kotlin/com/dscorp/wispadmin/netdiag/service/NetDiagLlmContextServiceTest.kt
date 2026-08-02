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
import com.dscorp.wispadmin.netdiag.port.OntSubscriptionInfo
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    private val radiusImpactPort = mockk<com.dscorp.wispadmin.netdiag.port.NetDiagRadiusImpactPort>()
    private val gponContextBuilder = mockk<NetDiagLlmGponContextBuilder>()
    private val service = NetDiagLlmContextService(
        incidentRepository = incidentRepository,
        incidentEventRepository = incidentEventRepository,
        probeRunRepository = probeRunRepository,
        trapEventRepository = trapEventRepository,
        radiusImpactPort = radiusImpactPort,
        gponContextBuilder = gponContextBuilder,
        objectMapper = ObjectMapper()
    )

    @BeforeEach
    fun stubTraps() {
        every { trapEventRepository.findTop20ByTargetIdOrderByReceivedAtDesc(any()) } returns emptyList()
        every { radiusImpactPort.estimateImpact(any(), any()) } returns com.dscorp.wispadmin.netdiag.port.RadiusImpactSnapshot(
            source = "wispadmin-subscriptions",
            activeSubscriptions = 1200L,
            pppActiveSessions = 45,
            estimatedAffected = 45L,
            notes = "PPP activas desde último probe_run del target"
        )
        every { gponContextBuilder.build(any()) } returns null
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
        every { incidentRepository.findByIdWithTarget(42L) } returns Optional.of(incident)
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
        assertTrue(markdown.contains("Impacto suscriptores"))
    }

    @Test
    fun `buildDiagnosticJson retorna mapa estructurado`() {
        every { incidentRepository.findByIdWithTarget(42L) } returns Optional.of(incident)
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
        assertTrue(json.containsKey("radiusImpact"))
    }

    @Test
    fun `incidente inexistente lanza IncidentNotFoundException`() {
        every { incidentRepository.findByIdWithTarget(99L) } returns Optional.empty()
        assertThrows<IncidentNotFoundException> { service.buildMarkdown(99L) }
    }

    private val ponTarget = NetDiagTarget(
        id = 4L,
        name = "PON-gigafiber-ma5608t-gpon-0/0",
        deviceRefId = 1_001_000L,
        parentTargetId = 3L,
        monitorConfig = """{"kind":"pon","oltId":"gigafiber-ma5608t","board":0,"port":0}"""
    )

    private val ponIncident = NetDiagIncident(
        id = 387L,
        target = ponTarget,
        dedupKey = "ONT_CONFIG_RECOVERY_FAIL:4:gpon-0/0:ont-16",
        status = "OPEN",
        severity = "P2",
        title = "ONT_CONFIG_RECOVERY_FAIL gpon-0/0 ont=16",
        reasonCode = "ONT_CONFIG_RECOVERY_FAIL",
        openedAt = Instant.parse("2026-08-01T23:03:13Z")
    )

    private val gponContext = GponLlmContext(
        targetContext = GponTargetContext(
            kind = "pon",
            oltId = "gigafiber-ma5608t",
            board = 0,
            port = 0,
            mgmtIp = null,
            parentTargetName = "OLT-gigafiber-ma5608t"
        ),
        recentOltLogs = listOf(
            GponOltLogEntry(
                receivedAt = Instant.parse("2026-08-01T23:03:00Z"),
                reasonCode = "ONT_CONFIG_RECOVERY_FAIL",
                onuIndex = 16,
                severity = "P2",
                alarmName = "The GPON ONT configuration recovery fails",
                isClear = false,
                rawMessage = "ALARM 453796 FAULT WARNING"
            )
        ),
        ponInventory = PonInventory(
            total = 12,
            online = 10,
            offline = 2,
            offlineSample = listOf(
                PonOnuSummary(
                    onuIndex = 3,
                    sn = "HWTC00000003",
                    runState = "offline",
                    lastDownCause = "dying-gasp",
                    onuRxDbm = -21.1
                )
            )
        ),
        ontSubscription = OntSubscriptionContext(
            onuIndex = 16,
            sn = "HWTC00000016",
            runState = "online",
            lastDownCause = null,
            subscription = OntSubscriptionInfo(
                subscriptionId = 555,
                customerName = "Juan Perez",
                serviceStatus = "ACTIVE",
                napBoxCode = "NAP-05"
            )
        )
    )

    private fun stubPonIncident() {
        every { incidentRepository.findByIdWithTarget(387L) } returns Optional.of(ponIncident)
        every { incidentEventRepository.findByIncidentIdOrderByCreatedAtDesc(387L) } returns emptyList()
        every { gponContextBuilder.build(ponIncident) } returns gponContext
    }

    @Test
    fun `buildMarkdown pon incluye secciones gpon y oculta ruido mikrotik`() {
        stubPonIncident()

        val markdown = service.buildMarkdown(387L)

        assertTrue(markdown.contains("## Target GPON"))
        assertTrue(markdown.contains("gigafiber-ma5608t"))
        assertTrue(markdown.contains("OLT-gigafiber-ma5608t"))
        assertTrue(markdown.contains("## Alarmas OLT recientes"))
        assertTrue(markdown.contains("ONT_CONFIG_RECOVERY_FAIL"))
        assertTrue(markdown.contains("The GPON ONT configuration recovery fails"))
        assertTrue(markdown.contains("## Inventario PON"))
        assertTrue(markdown.contains("HWTC00000003"))
        assertTrue(markdown.contains("dying-gasp"))
        assertTrue(markdown.contains("## Abonado ONT"))
        assertTrue(markdown.contains("Juan Perez"))
        assertTrue(markdown.contains("NAP-05"))

        assertFalse(markdown.contains("No hay probe_run"))
        assertFalse(markdown.contains("Health snapshot"))
        assertFalse(markdown.contains("Netwatch snapshot"))
        assertFalse(markdown.contains("Optical snapshot"))
        assertFalse(markdown.contains("Impacto suscriptores"))
        assertFalse(markdown.contains("SNMP traps recientes"))
    }

    @Test
    fun `buildDiagnosticJson pon expone claves gpon y omite radius y probe`() {
        stubPonIncident()

        val json = service.buildDiagnosticJson(387L)

        assertEquals(387L, json["incidentId"])
        assertTrue(json.containsKey("targetContext"))
        assertTrue(json.containsKey("recentOltLogs"))
        assertTrue(json.containsKey("ponInventory"))
        assertTrue(json.containsKey("ontSubscription"))
        assertFalse(json.containsKey("radiusImpact"))
        assertFalse(json.containsKey("latestProbe"))
        assertFalse(json.containsKey("healthSnapshot"))
        assertFalse(json.containsKey("recentTraps"))

        @Suppress("UNCHECKED_CAST")
        val targetContext = json["targetContext"] as Map<String, Any?>
        assertEquals("pon", targetContext["kind"])
        assertEquals("gigafiber-ma5608t", targetContext["oltId"])

        @Suppress("UNCHECKED_CAST")
        val logs = json["recentOltLogs"] as List<Map<String, Any?>>
        assertEquals("ONT_CONFIG_RECOVERY_FAIL", logs.first()["reasonCode"])
        assertEquals(16, logs.first()["onuIndex"])

        @Suppress("UNCHECKED_CAST")
        val ontSubscription = json["ontSubscription"] as Map<String, Any?>
        assertEquals("HWTC00000016", ontSubscription["sn"])
        @Suppress("UNCHECKED_CAST")
        val subscription = ontSubscription["subscription"] as Map<String, Any?>
        assertEquals(555, subscription["subscriptionId"])
        assertEquals("Juan Perez", subscription["customerName"])
    }
}
