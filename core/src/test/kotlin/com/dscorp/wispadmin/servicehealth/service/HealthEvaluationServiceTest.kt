package com.dscorp.wispadmin.servicehealth.service

import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.HealthCurrent
import com.dscorp.wispadmin.servicehealth.domain.HealthCursor
import com.dscorp.wispadmin.servicehealth.dto.HealthSummary
import com.dscorp.wispadmin.servicehealth.port.HealthTrafficPort
import com.dscorp.wispadmin.servicehealth.port.SubscriptionDirectoryPort
import com.dscorp.wispadmin.servicehealth.repository.EvidenceLinkRepository
import com.dscorp.wispadmin.servicehealth.repository.HealthCurrentRepository
import com.dscorp.wispadmin.servicehealth.repository.HealthCursorRepository
import com.dscorp.wispadmin.servicehealth.repository.HealthEventRepository
import com.dscorp.wispadmin.servicehealth.repository.TelemetryRunRepository
import com.dscorp.wispadmin.servicehealth.repository.TrafficEvidenceRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.Optional
import java.util.function.Consumer

class HealthEvaluationServiceTest {

    private val properties = ServiceHealthProperties().apply {
        enabled = true
        correlationEnabled = false
        snapshotFreshSeconds = 60
    }
    private val subscriptions = mockk<SubscriptionDirectoryPort>()
    private val identity = mockk<IdentityService>(relaxed = true)
    private val reader = mockk<HealthEvidenceReader>()
    private val engine = mockk<DiagnosisEngine>()
    private val events = mockk<HealthEventRepository>(relaxed = true)
    private val current = mockk<HealthCurrentRepository>(relaxed = true)
    private val evidence = mockk<EvidenceLinkRepository>(relaxed = true)
    private val runs = mockk<TelemetryRunRepository>(relaxed = true)
    private val trafficPort = mockk<ObjectProvider<HealthTrafficPort>>()
    private val trafficEvidence = mockk<TrafficEvidenceRepository>(relaxed = true)
    private val cursors = mockk<HealthCursorRepository>()
    private val tx = mockk<TransactionTemplate>()
    private val json = ObjectMapper().findAndRegisterModules()

    private lateinit var service: HealthEvaluationService

    @BeforeEach
    fun setUp() {
        every { trafficPort.ifAvailable } returns null
        every { tx.executeWithoutResult(any()) } answers {
            firstArg<Consumer<TransactionStatus>>().accept(mockk(relaxed = true))
        }
        every { cursors.lock(any()) } returns HealthCursor(cursorKey = "evaluation")
        every { cursors.save(any()) } answers { firstArg() }
        every { current.save(any()) } answers { firstArg() }
        service = HealthEvaluationService(
            properties, subscriptions, identity, reader, engine, events, current, evidence, runs,
            trafficPort, trafficEvidence, cursors, tx, json,
        )
    }

    @Test
    fun `sweep skips engine when current snapshot is still fresh`() {
        val now = Instant.now()
        every { subscriptions.allIds() } returns listOf(10)
        every { subscriptions.exists(10) } returns true
        every { current.findById(10) } returns Optional.of(
            HealthCurrent(subscriptionId = 10, evaluatedAt = now.minusSeconds(15), summaryJson = "{}"),
        )

        service.evaluate()

        verify(exactly = 0) { reader.read(any(), any()) }
        verify(exactly = 0) { engine.evaluate(any()) }
        verify(exactly = 0) { identity.reconcile(10, any()) }
    }

    @Test
    fun `sweep evaluates when current is missing or stale`() {
        val now = Instant.now()
        every { subscriptions.allIds() } returns listOf(10)
        every { subscriptions.exists(10) } returns true
        every { current.findById(10) } returns Optional.of(
            HealthCurrent(subscriptionId = 10, evaluatedAt = now.minusSeconds(120), summaryJson = "{}"),
        )
        val inputs = mockk<HealthInputs>()
        every { inputs.sources } returns emptyList()
        every { reader.read(10, any()) } returns inputs
        every { engine.evaluate(inputs) } returns HealthSummary(
            subscriptionId = 10,
            evaluatedAt = now,
            states = emptyMap(),
            sources = emptyList(),
            diagnoses = emptyList(),
            missingEvidence = emptyList(),
            identity = emptyMap(),
            actionsEnabled = false,
        )

        service.evaluate()

        verify(exactly = 1) { engine.evaluate(inputs) }
        verify(exactly = 1) { current.save(any()) }
    }
}
