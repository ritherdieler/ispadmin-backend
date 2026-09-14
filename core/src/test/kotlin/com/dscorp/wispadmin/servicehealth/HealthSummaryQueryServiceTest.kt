package com.dscorp.wispadmin.servicehealth

import com.dscorp.wispadmin.events.HealthSnapshotCache
import com.dscorp.wispadmin.servicehealth.config.ServiceHealthProperties
import com.dscorp.wispadmin.servicehealth.domain.HealthCurrent
import com.dscorp.wispadmin.servicehealth.domain.HealthEvent
import com.dscorp.wispadmin.servicehealth.dto.HealthSummary
import com.dscorp.wispadmin.servicehealth.dto.ServiceContext
import com.dscorp.wispadmin.servicehealth.dto.SubscriberContext
import com.dscorp.wispadmin.servicehealth.dto.SubscriptionContext
import com.dscorp.wispadmin.servicehealth.repository.HealthCurrentRepository
import com.dscorp.wispadmin.servicehealth.repository.HealthEventRepository
import com.dscorp.wispadmin.servicehealth.repository.RemoteActionRepository
import com.dscorp.wispadmin.servicehealth.service.DiagnosisEngine
import com.dscorp.wispadmin.servicehealth.service.HealthEvidenceReader
import com.dscorp.wispadmin.servicehealth.service.HealthSummaryQueryService
import com.dscorp.wispadmin.servicehealth.service.ServiceHealthSubscriptionContextReader
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant
import java.util.Optional

class HealthSummaryQueryServiceTest {

    private val reader = mockk<HealthEvidenceReader>()
    private val engine = mockk<DiagnosisEngine>()
    private val current = mockk<HealthCurrentRepository>(relaxed = true)
    private val cache = mockk<HealthSnapshotCache>(relaxed = true)
    private val cacheProvider = mockk<ObjectProvider<HealthSnapshotCache>>()
    private val events = mockk<HealthEventRepository>()
    private val actions = mockk<RemoteActionRepository>(relaxed = true)
    private val contextReader = mockk<ServiceHealthSubscriptionContextReader>()
    private val json = jacksonObjectMapper().findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    private val properties = ServiceHealthProperties().apply { snapshotFreshSeconds = 60 }

    private val service = HealthSummaryQueryService(
        reader = reader,
        engine = engine,
        current = current,
        cache = cacheProvider,
        events = events,
        actions = actions,
        subscriptionContext = contextReader,
        properties = properties,
        json = json,
    )

    private val evaluatedAt = Instant.parse("2026-09-03T18:10:00Z")

    init {
        every { cacheProvider.ifUnique } returns cache
        every { events.findBySubscriptionIdAndEventStatus(42, "OPEN") } returns emptyList()
        every { contextReader.read(42) } returns SubscriptionContext(
            SubscriberContext("Ana", "PERSON"),
            ServiceContext("ACTIVE", "Fibra", "10.0.0.5"),
        )
        every { cache.getSummaryJson(42) } returns null
    }

    @Test
    fun `fresh snapshot does not call traffic reader`() {
        val stored = baseSummary().copy(states = mapOf("gpon" to "ONLINE"))
        every { current.findById(42) } returns Optional.of(
            HealthCurrent(42, evaluatedAt, json.writeValueAsString(stored))
        )

        val result = service.summary(42, now = evaluatedAt.plusSeconds(10))

        assertEquals("ONLINE", result.states["gpon"])
        assertEquals("Ana", result.subscriber?.displayName)
        verify(exactly = 0) { reader.read(any(), any()) }
        verify(exactly = 0) { engine.evaluate(any()) }
    }

    @Test
    fun `stale snapshot falls back to live evaluate and persists`() {
        val stale = baseSummary()
        every { current.findById(42) } returns Optional.of(
            HealthCurrent(42, evaluatedAt.minusSeconds(120), json.writeValueAsString(stale))
        )
        val live = baseSummary().copy(states = mapOf("gpon" to "DOWN"))
        every { reader.read(42, any()) } returns mockk()
        every { engine.evaluate(any()) } returns live
        every { current.save(any()) } answers { firstArg() }

        val result = service.summary(42, now = evaluatedAt)

        assertEquals("DOWN", result.states["gpon"])
        verify(exactly = 1) { reader.read(42, any()) }
        verify { current.save(match { it.subscriptionId == 42 && it.summaryJson.contains("DOWN") }) }
        verify { cache.putSummaryJson(42, any(), any()) }
    }

    private fun baseSummary() = HealthSummary(
        subscriptionId = 42,
        evaluatedAt = evaluatedAt,
        states = emptyMap(),
        sources = emptyList(),
        diagnoses = emptyList(),
        missingEvidence = emptyList(),
        identity = emptyMap(),
        pilotEnabled = false,
        actionsEnabled = false,
    )
}
