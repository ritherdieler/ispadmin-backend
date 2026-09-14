package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.CrmMetricsProperties
import com.dscorp.wispadmin.wispadmin.data.model.CrmAssignmentEvent
import com.dscorp.wispadmin.wispadmin.data.model.CrmAssignmentEventType
import com.dscorp.wispadmin.wispadmin.data.model.CrmChannel
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversation
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversationStatus
import com.dscorp.wispadmin.wispadmin.data.model.TicketConversationLink
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppAuditLog
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.dto.CsatSummaryDto
import com.dscorp.wispadmin.wispadmin.repository.AssistanceTicketRepository
import com.dscorp.wispadmin.wispadmin.repository.CrmAssignmentEventRepository
import com.dscorp.wispadmin.wispadmin.repository.CrmConversationRepository
import com.dscorp.wispadmin.wispadmin.repository.TicketConversationLinkRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppAuditLogRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CrmMetricsServiceTest {

    private val conversationRepository = mockk<CrmConversationRepository>()
    private val assignmentEventRepository = mockk<CrmAssignmentEventRepository>()
    private val messageLogRepository = mockk<WhatsAppMessageLogRepository>()
    private val auditLogRepository = mockk<WhatsAppAuditLogRepository>()
    private val ticketLinkRepository = mockk<TicketConversationLinkRepository>()
    private val ticketRepository = mockk<AssistanceTicketRepository>(relaxed = true)
    private val csatSurveyService = mockk<CsatSurveyService>()
    private val properties = CrmMetricsProperties().apply {
        firstResponseSlaMinutes = 15
        resolutionSlaMinutes = 240
        abandonmentPendingHours = 24
        excessiveWaitMinutes = 30
    }

    private lateinit var service: CrmMetricsService

    private val from = LocalDateTime.of(2026, 7, 1, 0, 0)
    private val to = LocalDateTime.of(2026, 8, 1, 0, 0)

    @BeforeEach
    fun setUp() {
        service = CrmMetricsService(
            conversationRepository,
            assignmentEventRepository,
            messageLogRepository,
            auditLogRepository,
            ticketLinkRepository,
            ticketRepository,
            csatSurveyService,
            properties
        )
        every { csatSurveyService.buildSummary(from, to) } returns CsatSummaryDto(
            from = from,
            to = to,
            scheduled = 0,
            sent = 2,
            answered = 1,
            expired = 0,
            failed = 0,
            responseRate = 0.5,
            averageScore = 4.0,
            byTechnician = emptyList(),
            byPlace = emptyList(),
            byCategory = emptyList(),
            evolution = emptyList(),
            reasons = emptyList()
        )
    }

    @Test
    fun `buildSummary computes resolution and reopen counts without double counting csat`() {
        val claimedAt = LocalDateTime.of(2026, 7, 10, 10, 0)
        val resolvedAt = LocalDateTime.of(2026, 7, 10, 11, 0)
        val conversation = CrmConversation(
            id = 1L,
            channel = CrmChannel.WHATSAPP,
            phone = "999888777",
            status = CrmConversationStatus.RESOLVED,
            assignedAgentId = null,
            claimedAt = claimedAt,
            resolvedAt = resolvedAt,
            subscriptionId = 10
        )
        every { conversationRepository.findByResolvedAtBetween(from, to) } returns listOf(conversation)
        every { conversationRepository.findByStatusIn(any()) } returns emptyList()
        every { assignmentEventRepository.findByCreatedAtBetween(from, to) } returns listOf(
            CrmAssignmentEvent(
                id = 1L,
                conversationId = 1L,
                eventType = CrmAssignmentEventType.REOPEN,
                createdAt = LocalDateTime.of(2026, 7, 5, 9, 0)
            )
        )
        every {
            messageLogRepository.findByMessageTypeInAndCreatedAtBetween(any(), from, to)
        } returns listOf(
            WhatsAppMessageLog(
                id = 1,
                phone = "999888777",
                messageType = WhatsAppConversationService.MESSAGE_TYPE_OPERATOR_REPLY,
                operatorUsername = "agent1",
                createdAt = LocalDateTime.of(2026, 7, 10, 10, 5)
            )
        )
        every { auditLogRepository.findByActionAndCreatedAtBetween(any(), from, to) } returns listOf(
            WhatsAppAuditLog(
                action = WhatsAppAuditService.ACTION_BOT_TRACE,
                phone = "999888777",
                details = "intent=DEBT_VIEW;source=rules;confidence=0.90;escalate=support_diagnostic",
                createdAt = LocalDateTime.of(2026, 7, 9, 8, 0)
            )
        )
        every { ticketLinkRepository.findByCreatedAtBetween(from, to) } returns listOf(
            TicketConversationLink(ticketId = 5, conversationId = 1L, createdAt = LocalDateTime.of(2026, 7, 10, 9, 0))
        )
        every { ticketRepository.findAllById(listOf(5)) } returns emptyList()

        val summary = service.buildSummary(from, to)

        assertEquals(1L, summary.resolvedConversations)
        assertEquals(1L, summary.reopenEvents)
        assertEquals(60.0, summary.avgResolutionMinutes)
        assertEquals(5.0, summary.avgFirstResponseMinutes)
        assertEquals(1L, summary.botToHumanTransfers)
        assertEquals(1L, summary.ticketsFromConversations)
        assertEquals(0.5, summary.csatReference.responseRate)
        assertEquals(4.0, summary.csatReference.averageScore)
        assertEquals("/crm/csat", summary.csatReference.panelPath)
    }

    @Test
    fun `buildAgentBreakdown groups by assigned agent on resolve events`() {
        val events = listOf(
            CrmAssignmentEvent(
                conversationId = 1L,
                eventType = CrmAssignmentEventType.RESOLVE,
                fromUserId = 7,
                createdAt = LocalDateTime.of(2026, 7, 2, 12, 0)
            ),
            CrmAssignmentEvent(
                conversationId = 2L,
                eventType = CrmAssignmentEventType.RESOLVE,
                fromUserId = 7,
                createdAt = LocalDateTime.of(2026, 7, 3, 12, 0)
            ),
            CrmAssignmentEvent(
                conversationId = 3L,
                eventType = CrmAssignmentEventType.CLAIM,
                toUserId = 8,
                createdAt = LocalDateTime.of(2026, 7, 4, 12, 0)
            )
        )
        every { assignmentEventRepository.findByCreatedAtBetween(from, to) } returns events
        every { conversationRepository.findByStatusIn(listOf(CrmConversationStatus.ASSIGNED)) } returns emptyList()

        val agents = service.buildAgentBreakdown(from, to)

        assertEquals(2, agents.size)
        val agent7 = agents.first { it.agentId == 7 }
        assertEquals(2L, agent7.resolvedCount)
        assertEquals(0L, agents.first { it.agentId == 8 }.activeAssignedCount)
        assertEquals(2, agents.size)
    }

    @Test
    fun `buildShiftHandoff includes pending and excessive wait alerts`() {
        val now = LocalDateTime.of(2026, 7, 15, 18, 0)
        val stalePending = CrmConversation(
            id = 3L,
            phone = "911111111",
            status = CrmConversationStatus.PENDING,
            lastInboundAt = now.minusMinutes(45),
            updatedAt = now.minusMinutes(45)
        )
        val windowStart = now.minusHours(8)
        every { conversationRepository.findByStatusIn(any()) } returns listOf(stalePending)
        every { conversationRepository.findByResolvedAtBetween(windowStart, now) } returns emptyList()
        every { assignmentEventRepository.findByCreatedAtBetween(windowStart, now) } returns emptyList()
        every { auditLogRepository.findByActionAndCreatedAtBetween(any(), windowStart, now) } returns emptyList()

        val handoff = service.buildShiftHandoff(windowStart, now)

        assertEquals(1L, handoff.pendingUnassigned)
        assertTrue(handoff.excessiveWaitAlerts.any { it.conversationId == 3L })
    }
}
