package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagAlertDecision
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncidentEvent
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagAlertDecisionRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentEventRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagIncidentRepository
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagTargetRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

class AlertEvaluatorTest {

    private val incidentRepository = mockk<NetDiagIncidentRepository>()
    private val incidentEventRepository = mockk<NetDiagIncidentEventRepository>()
    private val alertDecisionRepository = mockk<NetDiagAlertDecisionRepository>()
    private val targetRepository = mockk<NetDiagTargetRepository>()
    private val notifier = mockk<WhatsAppOpsNotifier>(relaxed = true)
    private val properties = NetDiagProperties().apply {
        alert.parentMaxDepth = 5
    }
    private val correlationEngine = CorrelationEngine(incidentRepository, targetRepository, properties)
    private val evaluator = AlertEvaluator(
        incidentRepository = incidentRepository,
        incidentEventRepository = incidentEventRepository,
        alertDecisionRepository = alertDecisionRepository,
        targetRepository = targetRepository,
        correlationEngine = correlationEngine,
        notifier = notifier
    )

    private val idSeq = AtomicLong(100)

    private val child = NetDiagTarget(id = 2L, name = "PON", deviceRefId = 20L, parentTargetId = 1L)
    private val parent = NetDiagTarget(id = 1L, name = "OLT", deviceRefId = 10L)

    @BeforeEach
    fun setup() {
        every { targetRepository.findById(2L) } returns Optional.of(child)
        every { targetRepository.findById(1L) } returns Optional.of(parent)
        every { incidentRepository.findByTarget_IdAndStatus(2L, "OPEN") } returns emptyList()
        every { incidentRepository.findByTarget_IdAndStatus(1L, "OPEN") } returns emptyList()
        every { alertDecisionRepository.save(any()) } answers {
            firstArg<NetDiagAlertDecision>().also { if (it.id == null) it.id = idSeq.incrementAndGet() }
        }
        every { incidentEventRepository.save(any()) } answers {
            firstArg<NetDiagIncidentEvent>().also { if (it.id == null) it.id = idSeq.incrementAndGet() }
        }
    }

    @Test
    fun `abre incidente nuevo y registra decision OPEN y evento`() {
        val signal = AlertSignal(
            reasonCode = "LINK_DOWN",
            severity = "P0",
            title = "Link down: ether1",
            dedupKey = "LINK_DOWN:2:ether1"
        )
        every { incidentRepository.findByDedupKeyAndStatus("LINK_DOWN:2:ether1", "OPEN") } returns Optional.empty()
        every { incidentRepository.existsByTarget_IdAndStatus(1L, "OPEN") } returns false
        val incidentSlot = slot<NetDiagIncident>()
        every { incidentRepository.save(capture(incidentSlot)) } answers {
            firstArg<NetDiagIncident>().also { it.id = 55L }
        }

        val result = evaluator.evaluate(2L, listOf(signal))

        assertEquals(listOf("OPEN"), result.decisions)
        assertEquals(listOf(55L), result.openedIncidentIds)
        assertEquals("OPEN", incidentSlot.captured.status)
        verify { notifier.notifyIfNeeded(any()) }
    }

    @Test
    fun `dedup adjunta evento a incidente OPEN existente`() {
        val existing = NetDiagIncident(
            id = 55L,
            target = child,
            dedupKey = "LINK_DOWN:2:ether1",
            status = "OPEN",
            severity = "P0",
            title = "Link down: ether1",
            reasonCode = "LINK_DOWN"
        )
        val signal = AlertSignal(
            reasonCode = "LINK_DOWN",
            severity = "P0",
            title = "Link down: ether1",
            dedupKey = "LINK_DOWN:2:ether1"
        )
        every { incidentRepository.findByDedupKeyAndStatus("LINK_DOWN:2:ether1", "OPEN") } returns Optional.of(existing)
        every { incidentRepository.existsByTarget_IdAndStatus(1L, "OPEN") } returns false

        val result = evaluator.evaluate(2L, listOf(signal))

        assertEquals(listOf("CONTINUE"), result.decisions)
        verify { incidentEventRepository.save(match { it.type == "ALERT_SEEN" }) }
        verify(exactly = 0) { incidentRepository.save(any()) }
    }

    @Test
    fun `supresion padre-hijo registra SUPPRESSED_CHILD en incidente ancestro`() {
        val parentIncident = NetDiagIncident(
            id = 10L,
            target = parent,
            dedupKey = "DEVICE_UNREACHABLE:1:poll",
            status = "OPEN",
            severity = "P0",
            title = "OLT down",
            reasonCode = "DEVICE_UNREACHABLE"
        )
        val signal = AlertSignal(
            reasonCode = "PON_DOWN",
            severity = "P0",
            title = "PON down",
            dedupKey = "PON_DOWN:2:gpon-0/1"
        )
        every { incidentRepository.existsByTarget_IdAndStatus(1L, "OPEN") } returns true
        every { incidentRepository.findByTarget_IdAndStatus(1L, "OPEN") } returns listOf(parentIncident)

        val result = evaluator.evaluate(2L, listOf(signal))

        assertTrue(result.suppressed)
        assertEquals(listOf("SUPPRESSED"), result.decisions)
        verify {
            incidentEventRepository.save(match {
                it.type == "SUPPRESSED_CHILD" && it.incident.id == 10L
            })
        }
        verify(exactly = 0) { incidentRepository.save(any()) }
    }
}
