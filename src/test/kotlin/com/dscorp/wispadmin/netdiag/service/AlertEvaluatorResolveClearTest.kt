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
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

class AlertEvaluatorResolveClearTest {

    private val incidentRepository = mockk<NetDiagIncidentRepository>()
    private val incidentEventRepository = mockk<NetDiagIncidentEventRepository>()
    private val alertDecisionRepository = mockk<NetDiagAlertDecisionRepository>()
    private val targetRepository = mockk<NetDiagTargetRepository>()
    private val notifier = mockk<WhatsAppOpsNotifier>(relaxed = true)
    private val llmWebhookService = mockk<NetDiagLlmWebhookService>(relaxed = true)
    private val properties = NetDiagProperties()
    private val correlationEngine = CorrelationEngine(incidentRepository, targetRepository, properties)
    private val evaluator = AlertEvaluator(
        incidentRepository = incidentRepository,
        incidentEventRepository = incidentEventRepository,
        alertDecisionRepository = alertDecisionRepository,
        targetRepository = targetRepository,
        correlationEngine = correlationEngine,
        notifier = notifier,
        llmWebhookService = llmWebhookService
    )
    private val idSeq = AtomicLong(1)
    private val target = NetDiagTarget(id = 5L, name = "PON", deviceRefId = 1L)

    @BeforeEach
    fun setup() {
        every { incidentEventRepository.save(any()) } answers {
            firstArg<NetDiagIncidentEvent>().also { if (it.id == null) it.id = idSeq.incrementAndGet() }
        }
        every { incidentRepository.save(any()) } answers { firstArg() }
        every { alertDecisionRepository.save(any()) } answers {
            firstArg<NetDiagAlertDecision>().also { if (it.id == null) it.id = idSeq.incrementAndGet() }
        }
    }

    @Test
    fun `resolveByDedupKey cierra incidente OPEN`() {
        val incident = NetDiagIncident(
            id = 3L,
            target = target,
            dedupKey = "PON_PORT_DOWN:5:gpon-0/3",
            status = "OPEN",
            severity = "P0",
            title = "LOS",
            reasonCode = "PON_PORT_DOWN"
        )
        every {
            incidentRepository.findByDedupKeyAndStatus("PON_PORT_DOWN:5:gpon-0/3", "OPEN")
        } returns Optional.of(incident)

        assertTrue(evaluator.resolveByDedupKey("PON_PORT_DOWN:5:gpon-0/3", "clear from olt"))
        assertEquals("RESOLVED", incident.status)
        verify {
            incidentEventRepository.save(match { it.type == "CLEARED" })
        }
    }

    @Test
    fun `resolveByDedupKey retorna false si no hay OPEN`() {
        every {
            incidentRepository.findByDedupKeyAndStatus("PON_PORT_DOWN:5:gpon-0/3", "OPEN")
        } returns Optional.empty()

        assertFalse(evaluator.resolveByDedupKey("PON_PORT_DOWN:5:gpon-0/3", null))
    }
}
