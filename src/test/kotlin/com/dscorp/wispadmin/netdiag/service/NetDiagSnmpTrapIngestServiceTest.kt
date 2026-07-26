package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTrapEvent
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTrapEventRepository
import com.dscorp.wispadmin.netdiag.dto.TrapIngestRequestDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetDiagSnmpTrapIngestServiceTest {

    private val trapRepository = mockk<NetDiagTrapEventRepository>()
    private val signalExtractor = mockk<AlertSignalExtractor>()
    private val alertEvaluator = mockk<AlertEvaluator>()
    private val service = NetDiagSnmpTrapIngestService(
        trapRepository = trapRepository,
        signalExtractor = signalExtractor,
        alertEvaluator = alertEvaluator
    )

    @Test
    fun `ingest persiste trap y abre incidente SNMP_TRAP_LINK_DOWN`() {
        val saved = slot<NetDiagTrapEvent>()
        every { trapRepository.save(capture(saved)) } answers {
            firstArg<NetDiagTrapEvent>().also { it.id = 9L }
        }
        val signal = AlertSignal(
            reasonCode = "SNMP_TRAP_LINK_DOWN",
            severity = "P0",
            title = "SNMP trap link down ether1",
            dedupKey = "SNMP_TRAP_LINK_DOWN:7:ether1"
        )
        every {
            signalExtractor.fromIngest(
                7L,
                "SNMP_TRAP_LINK_DOWN",
                "P0",
                "SNMP trap link down: ether1",
                "ether1",
                any()
            )
        } returns signal
        every { alertEvaluator.evaluateIngest(7L, listOf(signal)) } returns AlertEvaluationResult(
            decisions = listOf("OPEN"),
            openedIncidentIds = listOf(55L)
        )

        val request = TrapIngestRequestDto().apply {
            targetId = 7L
            trapType = "interfaces"
            sourceHost = "38.224.231.2"
            oid = "1.3.6.1.6.3.1.1.5.3"
            varBinds = """{"ifName":"ether1","ifOperStatus":"down"}"""
            component = "ether1"
        }

        val result = service.ingest(request)

        assertEquals(listOf("OPEN"), result.decisions)
        assertEquals(listOf(55L), result.openedIncidentIds)
        assertEquals("SNMP_TRAP_LINK_DOWN", saved.captured.reasonCode)
        assertEquals("interfaces", saved.captured.trapType)
        assertEquals(7L, saved.captured.targetId)
    }

    @Test
    fun `mapea start-trap a SNMP_TRAP_REBOOT y temp-exception a SNMP_TRAP_TEMP`() {
        assertEquals("SNMP_TRAP_REBOOT", service.mapReasonCode("start-trap", null))
        assertEquals("SNMP_TRAP_TEMP", service.mapReasonCode("temp-exception", null))
        assertEquals("SNMP_TRAP_LINK_DOWN", service.mapReasonCode("interfaces", "linkDown"))
        assertEquals("SNMP_TRAP_GENERIC", service.mapReasonCode("unknown", null))
    }

    @Test
    fun `parseUdpPayload extrae host oid y trapType`() {
        val payload = "snmptrap host=38.224.231.2 type=interfaces oid=1.3.6.1.6.3.1.1.5.3 ifName=ether1"
        val parsed = service.parseUdpPayload(payload)

        assertEquals("38.224.231.2", parsed.sourceHost)
        assertEquals("interfaces", parsed.trapType)
        assertEquals("1.3.6.1.6.3.1.1.5.3", parsed.oid)
        assertTrue(parsed.raw.contains("ether1"))
    }
}
