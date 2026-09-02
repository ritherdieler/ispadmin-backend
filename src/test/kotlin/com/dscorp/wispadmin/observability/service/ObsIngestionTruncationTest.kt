package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsEvent
import com.dscorp.wispadmin.observability.entity.ObsIssue
import com.dscorp.wispadmin.observability.repository.ObsEventRepository
import com.dscorp.wispadmin.observability.repository.ObsIssueRepository
import com.dscorp.wispadmin.wispadmin.observability.ReportedEvent
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ObsIngestionTruncationTest {

    private val issueRepository = mockk<ObsIssueRepository>()
    private val eventRepository = mockk<ObsEventRepository>()
    private val fingerprintService = mockk<ObsFingerprintService>()
    private val issueAlertHandler = mockk<ObsIssueAlertHandler>(relaxed = true)
    private val livePublisher = mockk<ObsLivePublisher>(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val properties = ObservabilityProperties()

    private val service = ObsIngestionService(
        issueRepository = issueRepository,
        eventRepository = eventRepository,
        fingerprintService = fingerprintService,
        livePublisher = livePublisher,
        issueAlertHandler = issueAlertHandler,
        properties = properties,
        objectMapper = objectMapper
    )

    private val savedEvent = slot<ObsEvent>()

    @BeforeEach
    fun setup() {
        properties.ingest.maxBreadcrumbs = 5
        properties.ingest.maxBreadcrumbsChars = 200
        properties.ingest.maxContextChars = 200
        properties.ingest.maxMessageChars = 50
        properties.ingest.maxStacktraceChars = 100
        every { fingerprintService.fingerprint(any(), any(), any(), any()) } returns "fp-1"
        every { issueRepository.findByFingerprint("fp-1") } returns null
        every { issueRepository.save(any()) } answers {
            firstArg<ObsIssue>().also { it.id = 1L }
        }
        every { eventRepository.save(capture(savedEvent)) } answers {
            firstArg<ObsEvent>().also { it.id = 2L }
        }
    }

    private fun event(
        message: String? = "boom",
        stacktrace: String? = null,
        breadcrumbs: List<Any?>? = null,
        context: Map<String, Any?>? = null
    ) = ReportedEvent(
        eventType = "error",
        platform = "backoffice",
        severity = "error",
        message = message,
        errorType = "TypeError",
        stacktrace = stacktrace,
        breadcrumbs = breadcrumbs,
        context = context
    )

    @Test
    fun `conserva solo los ultimos breadcrumbs configurados`() {
        service.persistEvent(event(breadcrumbs = (1..40).map { mapOf("i" to it) }))

        val breadcrumbs = objectMapper.readTree(savedEvent.captured.breadcrumbsJson)
        assertEquals(5, breadcrumbs.size())
        assertEquals(40, breadcrumbs.last()["i"].asInt())
    }

    @Test
    fun `sustituye los breadcrumbs que superan el limite de tamano por un marcador valido`() {
        service.persistEvent(
            event(breadcrumbs = (1..5).map { mapOf("payload" to "x".repeat(500)) })
        )

        val breadcrumbs = objectMapper.readTree(savedEvent.captured.breadcrumbsJson)
        assertTrue(breadcrumbs["truncated"].asBoolean())
    }

    @Test
    fun `sustituye el contexto que supera el limite por un marcador valido`() {
        service.persistEvent(event(context = mapOf("blob" to "y".repeat(1_000))))

        val context = objectMapper.readTree(savedEvent.captured.contextJson)
        assertTrue(context["truncated"].asBoolean())
    }

    @Test
    fun `trunca mensaje y stacktrace a los limites configurados`() {
        service.persistEvent(event(message = "m".repeat(500), stacktrace = "s".repeat(500)))

        assertEquals(50, savedEvent.captured.message!!.length)
        assertEquals(100, savedEvent.captured.stacktrace!!.length)
    }

    @Test
    fun `deja intactos los payloads pequenos`() {
        service.persistEvent(
            event(breadcrumbs = listOf(mapOf("i" to 1)), context = mapOf("k" to "v"))
        )

        assertEquals("boom", savedEvent.captured.message)
        assertEquals("""[{"i":1}]""", savedEvent.captured.breadcrumbsJson)
        assertEquals("""{"k":"v"}""", savedEvent.captured.contextJson)
    }
}
