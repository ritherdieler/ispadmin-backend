package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagOltLogEvent
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagOltLogEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import com.dscorp.wispadmin.netdiag.port.NetDiagOltDescriptor
import com.dscorp.wispadmin.netdiag.port.NetDiagOltDescriptorPort
import com.dscorp.wispadmin.oltgateway.adapter.NetDiagOltAlarmParserAdapter
import com.dscorp.wispadmin.oltgateway.parser.HuaweiOltAlarmParser
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

class OltAlarmIngestServiceTest {

    private val repository = mockk<NetDiagOltLogEventRepository>()
    private val targetRepository = mockk<NetDiagTargetRepository>()
    private val incidentRepository = mockk<NetDiagIncidentRepository>()
    private val signalExtractor = mockk<AlertSignalExtractor>()
    private val alertEvaluator = mockk<AlertEvaluator>()
    private val parser = NetDiagOltAlarmParserAdapter(HuaweiOltAlarmParser())
    private val descriptor = object : NetDiagOltDescriptorPort {
        override fun descriptor() = NetDiagOltDescriptor(
            oltId = "gigafiber-ma5608t",
            host = "10.11.104.2",
            alarmPollEnabled = true,
            portsPerGponBoard = 16
        )
    }
    private val service = OltAlarmIngestService(
        logEventRepository = repository,
        parserProvider = availableProvider(parser),
        targetRepository = targetRepository,
        incidentRepository = incidentRepository,
        signalExtractor = signalExtractor,
        alertEvaluator = alertEvaluator,
        descriptorProvider = availableProvider(descriptor)
    )
    private val idSeq = AtomicLong(1)
    private val saved = mutableListOf<NetDiagOltLogEvent>()

    @BeforeEach
    fun setup() {
        saved.clear()
        every { repository.save(any()) } answers {
            firstArg<NetDiagOltLogEvent>().also {
                if (it.id == null) it.id = idSeq.incrementAndGet()
                saved += it
            }
        }
        every { repository.saveAll(any<List<NetDiagOltLogEvent>>()) } answers {
            firstArg<List<NetDiagOltLogEvent>>().map { event ->
                if (event.id == null) event.id = idSeq.incrementAndGet()
                saved += event
                event
            }
        }
        every { targetRepository.findByName(any()) } returns Optional.empty()
        every { targetRepository.findByParentTargetId(any()) } returns emptyList()
        every { incidentRepository.findByTarget_IdInAndStatus(any(), "OPEN") } returns emptyList()
        every { alertEvaluator.evaluateIngest(any(), any()) } returns AlertEvaluationResult(
            decisions = listOf("OPEN"),
            openedIncidentIds = listOf(99L)
        )
        every { alertEvaluator.resolveByDedupKey(any(), any()) } returns true
        every {
            signalExtractor.fromIngest(any(), any(), any(), any(), any(), any())
        } answers {
            val targetId = firstArg<Long?>()
            val reason = secondArg<String>()
            val component = arg<String>(4)
            AlertSignal(
                reasonCode = reason,
                severity = thirdArg(),
                title = arg(3),
                dedupKey = "$reason:${targetId ?: "global"}:$component",
                details = arg(5)
            )
        }
    }

    @Test
    fun `persiste alarmas parseadas y unparsed del mismo dump`() {
        val raw = """
          ALARM 1 FAULT CRITICAL 0x2e11a001 SERVICE QUALITY 2026-07-31 10:00:00-05:00
          ALARM NAME  : The feeder fiber is broken or OLT can not receive any expected optical signals(LOS)
          PARAMETERS  : FrameID: 0, SlotID: 0, PortID: 3
          --- END
          fragmento ilegible de la olt
          --- END
        """.trimIndent()
        val pon = NetDiagTarget(
            id = 55L,
            name = OltNetDiagTargetSyncService.ponTargetName("gigafiber-ma5608t", 0, 3),
            deviceRefId = 1L
        )
        every { targetRepository.findByName(pon.name) } returns Optional.of(pon)

        val result = service.ingestCliActiveAlarms(
            raw = raw,
            sourceIp = "10.11.104.2",
            oltTargetId = 42L,
            channel = "cli_alarm_active"
        )

        assertEquals(2, result.persisted)
        assertEquals(1, result.parsed)
        assertEquals(1, result.unparsed)
        assertEquals(1, result.alertsEmitted)
        assertTrue(saved.any { it.reasonCode == "PON_PORT_DOWN" && it.targetId == 55L })
        assertTrue(saved.any { it.reasonCode == "OLT_ALARM_UNPARSED" })
        verify(exactly = 1) { alertEvaluator.evaluateIngest(55L, any()) }
        verify(exactly = 0) {
            alertEvaluator.evaluateIngest(any(), match { signals ->
                signals.any { it.reasonCode == "OLT_ALARM_UNPARSED" }
            })
        }
    }

    @Test
    fun `raw vacio no persiste nada`() {
        val result = service.ingestCliActiveAlarms("", null, null, "cli_alarm_active")
        assertEquals(0, result.persisted)
        verify(exactly = 0) { repository.saveAll(any<List<NetDiagOltLogEvent>>()) }
        verify(exactly = 0) { alertEvaluator.evaluateIngest(any(), any()) }
    }

    @Test
    fun `dump sin estructura se guarda entero como unparsed sin alerta`() {
        val raw = "%%ddModule/1/foo: unexplained binary-ish text"
        val slot = slot<List<NetDiagOltLogEvent>>()
        every { repository.saveAll(capture(slot)) } answers {
            firstArg<List<NetDiagOltLogEvent>>().onEach {
                if (it.id == null) it.id = idSeq.incrementAndGet()
                saved += it
            }
        }

        val result = service.ingestCliActiveAlarms(raw, "10.11.104.2", 7L, "syslog")

        assertEquals(1, result.persisted)
        assertEquals(1, result.unparsed)
        assertEquals(0, result.alertsEmitted)
        assertEquals("OLT_ALARM_UNPARSED", slot.captured.single().reasonCode)
        verify(exactly = 0) { alertEvaluator.evaluateIngest(any(), any()) }
    }

    @Test
    fun `clear resolve incidente abierto por dedupKey`() {
        val raw = """
          ALARM 2 FAULT WARNING 0x2e122007 SERVICE QUALITY 2026-07-31 11:00:00-05:00
          ALARM NAME  : OLT can receive expected optical signals from ONT(LOSi/LOBi) recovers
          PARAMETERS  : FrameID: 0, SlotID: 1, PortID: 8, ONT ID: 2
          --- END
        """.trimIndent()
        val pon = NetDiagTarget(
            id = 8L,
            name = OltNetDiagTargetSyncService.ponTargetName("gigafiber-ma5608t", 1, 8),
            deviceRefId = 2L
        )
        every { targetRepository.findByName(pon.name) } returns Optional.of(pon)

        val result = service.ingestCliActiveAlarms(raw, "10.11.104.2", 42L)

        assertEquals(1, result.cleared)
        verify {
            alertEvaluator.resolveByDedupKey(
                "ONT_OFFLINE:8:gpon-1/8:ont-2",
                any()
            )
        }
        verify(exactly = 0) { alertEvaluator.evaluateIngest(any(), any()) }
    }

    @Test
    fun `reconcilia incidente OPEN que ya no esta en active`() {
        val raw = """
          ALARM 1 FAULT CRITICAL 0x2e11a001 SERVICE QUALITY 2026-07-31 10:00:00-05:00
          ALARM NAME  : The feeder fiber is broken or OLT can not receive any expected optical signals(LOS)
          PARAMETERS  : FrameID: 0, SlotID: 0, PortID: 3
          --- END
        """.trimIndent()
        val pon = NetDiagTarget(
            id = 55L,
            name = OltNetDiagTargetSyncService.ponTargetName("gigafiber-ma5608t", 0, 3),
            deviceRefId = 1L
        )
        every { targetRepository.findByName(pon.name) } returns Optional.of(pon)
        every { targetRepository.findByParentTargetId(42L) } returns listOf(pon)
        val stale = NetDiagIncident(
            id = 11L,
            target = pon,
            dedupKey = "ONT_OFFLINE:55:gpon-0/3:ont-9",
            status = "OPEN",
            severity = "P1",
            title = "stale",
            reasonCode = "ONT_OFFLINE"
        )
        every { incidentRepository.findByTarget_IdInAndStatus(listOf(42L, 55L), "OPEN") } returns listOf(stale)

        val result = service.ingestCliActiveAlarms(raw, "10.11.104.2", 42L)

        assertTrue(result.cleared >= 1)
        verify { alertEvaluator.resolveByDedupKey("ONT_OFFLINE:55:gpon-0/3:ont-9", any()) }
    }

    @Test
    fun `sin parser no persiste alarmas`() {
        val isolated = OltAlarmIngestService(
            logEventRepository = repository,
            parserProvider = emptyProvider(),
            targetRepository = targetRepository,
            incidentRepository = incidentRepository,
            signalExtractor = signalExtractor,
            alertEvaluator = alertEvaluator,
            descriptorProvider = emptyProvider()
        )

        val result = isolated.ingestCliActiveAlarms("ALARM", "10.11.104.2", 42L)

        assertEquals(0, result.persisted)
        verify(exactly = 0) { repository.saveAll(any<List<NetDiagOltLogEvent>>()) }
    }

    private fun <T : Any> availableProvider(value: T): ObjectProvider<T> {
        val provider = mockk<ObjectProvider<T>>()
        every { provider.ifAvailable } returns value
        return provider
    }

    private fun <T : Any> emptyProvider(): ObjectProvider<T> {
        val provider = mockk<ObjectProvider<T>>()
        every { provider.ifAvailable } returns null
        return provider
    }
}
