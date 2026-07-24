package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsEvent
import com.dscorp.wispadmin.observability.port.ReportedEvent
import com.dscorp.wispadmin.observability.repository.ObsEventRepository
import com.dscorp.wispadmin.observability.repository.ObsIssueRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ObsIngestionServiceWorkflowTest {

    private val issueRepository = mockk<ObsIssueRepository>()
    private val eventRepository = mockk<ObsEventRepository>()
    private val fingerprintService = mockk<ObsFingerprintService>()
    private val livePublisher = mockk<ObsLivePublisher>(relaxed = true)
    private val issueAlertHandler = mockk<ObsIssueAlertHandler>(relaxed = true)
    private val properties = mockk<ObservabilityProperties>(relaxed = true)
    private val objectMapper = ObjectMapper()

    private val service = ObsIngestionService(
        issueRepository,
        eventRepository,
        fingerprintService,
        livePublisher,
        issueAlertHandler,
        properties,
        objectMapper
    )

    @Test
    fun `promueve tags de workflow a columnas indexadas`() {
        every { fingerprintService.fingerprint(any(), any(), any(), any()) } returns "fp-1"
        val saved = slot<ObsEvent>()
        every { eventRepository.save(capture(saved)) } answers { firstArg() }

        val issueId = service.persistEvent(
            ReportedEvent(
                eventType = "workflow_start",
                platform = "android",
                severity = "info",
                message = "login",
                errorType = null,
                stacktrace = null,
                tags = mapOf(
                    "workflowId" to "wf-123",
                    "workflowName" to "login",
                    "workflowCategory" to "auth",
                    "workflowStatus" to "success"
                )
            )
        )

        assertNull(issueId)
        assertEquals("wf-123", saved.captured.workflowId)
        assertEquals("login", saved.captured.workflowName)
        assertEquals("auth", saved.captured.workflowCategory)
        assertEquals("success", saved.captured.workflowStatus)
        verify(exactly = 0) { issueRepository.save(any()) }
        verify(exactly = 0) { livePublisher.publishEvent(any(), any()) }
    }

    @Test
    fun `eventos de error siguen creando issue`() {
        every { fingerprintService.fingerprint(any(), any(), any(), any()) } returns "fp-err"
        every { issueRepository.findByFingerprint("fp-err") } returns null
        every { issueRepository.save(any()) } answers {
            firstArg<com.dscorp.wispadmin.observability.entity.ObsIssue>().also { it.id = 9L }
        }
        every { eventRepository.save(any()) } answers { firstArg() }

        val issueId = service.persistEvent(
            ReportedEvent(
                eventType = "error",
                platform = "android",
                severity = "error",
                message = "boom",
                errorType = "RuntimeException",
                stacktrace = "at Foo",
                tags = mapOf("workflowId" to "wf-9", "workflowName" to "login", "workflowCategory" to "auth")
            )
        )

        assertEquals(9L, issueId)
        verify(exactly = 1) { issueRepository.save(any()) }
    }
}
