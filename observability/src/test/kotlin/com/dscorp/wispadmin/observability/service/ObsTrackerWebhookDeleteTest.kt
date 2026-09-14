package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.adapter.jira.JiraIssueTrackerAdapter
import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsIssue
import com.dscorp.wispadmin.observability.entity.ObsIssueStatus
import com.dscorp.wispadmin.observability.port.TrackerEventType
import com.dscorp.wispadmin.observability.repository.ObsIssueRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class ObsTrackerWebhookDeleteTest {

    private lateinit var issueRepository: ObsIssueRepository
    private lateinit var webhookService: ObsTrackerWebhookService
    private lateinit var adapter: JiraIssueTrackerAdapter

    private val ticketKey = "OBS-123"

    @BeforeEach
    fun setUp() {
        issueRepository = mock(ObsIssueRepository::class.java)
        webhookService = ObsTrackerWebhookService(issueRepository)
        adapter = JiraIssueTrackerAdapter(
            ObservabilityProperties(),
            ObjectMapper(),
            mock(com.dscorp.wispadmin.observability.tracing.TracingClientHttpRequestInterceptor::class.java)
        )
    }

    private fun jiraDeletePayload(): String = """
        {
          "timestamp": 1720000000000,
          "webhookEvent": "jira:issue_deleted",
          "issue": {
            "key": "$ticketKey",
            "fields": {
              "summary": "Error de prueba"
            }
          }
        }
    """.trimIndent()

    @Test
    fun `el adapter detecta el evento de borrado de Jira y extrae la clave`() {
        val event = adapter.parseWebhook(emptyMap(), jiraDeletePayload())

        assertNotNull(event)
        assertEquals(TrackerEventType.TICKET_DELETED, event!!.type)
        assertEquals(ticketKey, event.ticketKey)
    }

    @Test
    fun `al borrar el ticket en Jira se limpian los campos del tracker y se guarda el issue`() {
        val issue = ObsIssue(
            id = 10L,
            fingerprint = "fp-1",
            status = ObsIssueStatus.OPEN,
            trackerProvider = "jira",
            trackerIssueKey = ticketKey,
            trackerBrowseUrl = "https://acme.atlassian.net/browse/$ticketKey",
            jiraIssueKey = ticketKey
        )
        `when`(issueRepository.findByTrackerIssueKey(ticketKey)).thenReturn(issue)

        val event = adapter.parseWebhook(emptyMap(), jiraDeletePayload())
        val applied = webhookService.apply(event!!)

        assertTrue(applied)

        val captor = ArgumentCaptor.forClass(ObsIssue::class.java)
        verify(issueRepository, times(1)).save(captor.capture())
        val saved = captor.value

        assertNull(saved.trackerProvider)
        assertNull(saved.trackerIssueKey)
        assertNull(saved.trackerBrowseUrl)
        assertNull(saved.jiraIssueKey)
        assertEquals(ObsIssueStatus.OPEN, saved.status)
    }

    @Test
    fun `si el ticket fue creado con jiraIssueKey legado tambien se resuelve por ese campo`() {
        val issue = ObsIssue(
            id = 11L,
            fingerprint = "fp-2",
            trackerIssueKey = null,
            jiraIssueKey = ticketKey
        )
        `when`(issueRepository.findByTrackerIssueKey(ticketKey)).thenReturn(null)
        `when`(issueRepository.findByJiraIssueKey(ticketKey)).thenReturn(issue)

        val event = adapter.parseWebhook(emptyMap(), jiraDeletePayload())
        val applied = webhookService.apply(event!!)

        assertTrue(applied)
        verify(issueRepository, times(1)).save(issue)
        assertNull(issue.jiraIssueKey)
    }

    @Test
    fun `si no existe un issue con ese ticket no se guarda nada`() {
        `when`(issueRepository.findByTrackerIssueKey(ticketKey)).thenReturn(null)
        `when`(issueRepository.findByJiraIssueKey(ticketKey)).thenReturn(null)

        val event = adapter.parseWebhook(emptyMap(), jiraDeletePayload())
        val applied = webhookService.apply(event!!)

        assertFalse(applied)
        verify(issueRepository, never()).save(org.mockito.ArgumentMatchers.any())
    }
}
