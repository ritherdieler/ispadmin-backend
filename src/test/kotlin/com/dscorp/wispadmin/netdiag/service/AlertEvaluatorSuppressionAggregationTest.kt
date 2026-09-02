package com.dscorp.wispadmin.netdiag.service

import com.dscorp.wispadmin.netdiag.config.NetDiagProperties
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagAlertSuppressionWindow
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncident
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagIncidentEvent
import com.dscorp.wispadmin.netdiag.domain.entity.NetDiagTarget
import com.dscorp.wispadmin.netdiag.domain.repository.NetDiagAlertSuppressionWindowRepository
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
import java.time.Duration
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong

class AlertEvaluatorSuppressionAggregationTest {

    private val incidentRepository = mockk<NetDiagIncidentRepository>()
    private val incidentEventRepository = mockk<NetDiagIncidentEventRepository>()
    private val suppressionRepository = mockk<NetDiagAlertSuppressionWindowRepository>()
    private val targetRepository = mockk<NetDiagTargetRepository>()
    private val notifier = mockk<WhatsAppOpsNotifier>(relaxed = true)
    private val llmWebhookService = mockk<NetDiagLlmWebhookService>(relaxed = true)
    private val properties = NetDiagProperties().apply {
        alert.parentMaxDepth = 5
        alert.suppressionWindowMinutes = 60
    }
    private val summaryCache = NetDiagIncidentSummaryCache(properties)
    private val correlationEngine = CorrelationEngine(incidentRepository, targetRepository, properties)
    private val evaluator = AlertEvaluator(
        incidentRepository = incidentRepository,
        incidentEventRepository = incidentEventRepository,
        suppressionWindowRepository = suppressionRepository,
        targetRepository = targetRepository,
        correlationEngine = correlationEngine,
        notifier = notifier,
        llmWebhookService = llmWebhookService,
        summaryCache = summaryCache,
        properties = properties
    )

    private val idSeq = AtomicLong(100)
    private val child = NetDiagTarget(id = 2L, name = "PON", deviceRefId = 20L, parentTargetId = 1L)
    private val parent = NetDiagTarget(id = 1L, name = "OLT", deviceRefId = 10L)

    private val parentIncident = NetDiagIncident(
        id = 10L,
        target = parent,
        dedupKey = "DEVICE_UNREACHABLE:1:poll",
        status = "OPEN",
        severity = "P0",
        title = "OLT down",
        reasonCode = "DEVICE_UNREACHABLE"
    )

    private val signal = AlertSignal(
        reasonCode = "PON_DOWN",
        severity = "P0",
        title = "PON down",
        dedupKey = "PON_DOWN:2:gpon-0/1"
    )

    @BeforeEach
    fun setup() {
        every { targetRepository.findById(2L) } returns Optional.of(child)
        every { targetRepository.findById(1L) } returns Optional.of(parent)
        every { incidentRepository.findByTarget_IdAndStatus(2L, "OPEN") } returns emptyList()
        every { incidentRepository.findByTarget_IdAndStatus(1L, "OPEN") } returns listOf(parentIncident)
        every {
            incidentRepository.findByTarget_IdAndStatusAndReasonCode(any(), any(), any())
        } returns emptyList()
        every { incidentEventRepository.save(any()) } answers {
            firstArg<NetDiagIncidentEvent>().also { if (it.id == null) it.id = idSeq.incrementAndGet() }
        }
        every { suppressionRepository.save(any()) } answers {
            firstArg<NetDiagAlertSuppressionWindow>().also { if (it.id == null) it.id = idSeq.incrementAndGet() }
        }
    }

    @Test
    fun `una senal suprimida no escribe fila por evento en el historial del incidente`() {
        every { suppressionRepository.incrementWindow(any(), any(), any(), any(), any()) } returns 1

        val result = evaluator.evaluate(2L, listOf(signal))

        assertTrue(result.suppressed)
        assertEquals(listOf("SUPPRESSED"), result.decisions)
        verify(exactly = 0) { incidentEventRepository.save(any()) }
        verify(exactly = 0) { incidentRepository.save(any()) }
    }

    @Test
    fun `la primera supresion de la ventana crea el contador agregado`() {
        every { suppressionRepository.incrementWindow(any(), any(), any(), any(), any()) } returns 0
        val saved = slot<NetDiagAlertSuppressionWindow>()
        every { suppressionRepository.save(capture(saved)) } answers {
            firstArg<NetDiagAlertSuppressionWindow>().also { it.id = 7L }
        }

        evaluator.evaluate(2L, listOf(signal))

        assertEquals(10L, saved.captured.incidentId)
        assertEquals(2L, saved.captured.targetId)
        assertEquals("PON_DOWN", saved.captured.reasonCode)
        assertEquals(1L, saved.captured.eventCount)
    }

    @Test
    fun `las supresiones siguientes de la misma ventana solo incrementan el contador`() {
        every { suppressionRepository.incrementWindow(any(), any(), any(), any(), any()) } returns 1

        evaluator.evaluate(2L, listOf(signal))
        evaluator.evaluate(2L, listOf(signal))
        evaluator.evaluate(2L, listOf(signal))

        verify(exactly = 3) { suppressionRepository.incrementWindow(10L, 2L, "PON_DOWN", any(), any()) }
        verify(exactly = 0) { suppressionRepository.save(any()) }
    }

    @Test
    fun `la ventana se alinea al inicio del intervalo configurado`() {
        every { suppressionRepository.incrementWindow(any(), any(), any(), any(), any()) } returns 0
        val saved = slot<NetDiagAlertSuppressionWindow>()
        every { suppressionRepository.save(capture(saved)) } answers {
            firstArg<NetDiagAlertSuppressionWindow>().also { it.id = 7L }
        }

        evaluator.evaluate(2L, listOf(signal))

        val windowSeconds = Duration.ofMinutes(60).seconds
        assertEquals(0L, saved.captured.windowStart.epochSecond % windowSeconds)
    }
}
