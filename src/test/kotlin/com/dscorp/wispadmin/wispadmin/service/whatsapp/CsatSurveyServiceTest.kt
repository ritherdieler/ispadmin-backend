package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.CrmCsatProperties
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicket
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import com.dscorp.wispadmin.wispadmin.data.model.CsatDissatisfactionReason
import com.dscorp.wispadmin.wispadmin.data.model.CsatFollowUp
import com.dscorp.wispadmin.wispadmin.data.model.CsatFollowUpStatus
import com.dscorp.wispadmin.wispadmin.data.model.CsatSendChannel
import com.dscorp.wispadmin.wispadmin.data.model.CsatSurvey
import com.dscorp.wispadmin.wispadmin.data.model.CsatSurveyStatus
import com.dscorp.wispadmin.wispadmin.data.model.User
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.AssistanceTicketRepository
import com.dscorp.wispadmin.wispadmin.repository.CsatFollowUpRepository
import com.dscorp.wispadmin.wispadmin.repository.CsatSurveyRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.service.WhatsAppSendResult
import com.dscorp.wispadmin.wispadmin.service.WhatsAppService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional

class CsatSurveyServiceTest {

    private val surveyRepository = mockk<CsatSurveyRepository>()
    private val followUpRepository = mockk<CsatFollowUpRepository>()
    private val ticketRepository = mockk<AssistanceTicketRepository>()
    private val messageLogRepository = mockk<WhatsAppMessageLogRepository>()
    private val whatsAppService = mockk<WhatsAppService>()
    private val serviceWindowService = mockk<WhatsAppServiceWindowService>()
    private val eventPublisher = mockk<CrmEventPublisher>(relaxed = true)
    private val props = CrmCsatProperties().apply {
        enabled = true
        templateName = "csat_survey_v1"
        templateLanguage = "es_PE"
        expireHours = 72
        maxRetries = 3
        retryMinutes = 60
        delayMinutes = 0
        lowScoreThreshold = 2
        commentWindowMinutes = 30
    }

    private lateinit var service: CsatSurveyService
    private var idSeq = 1L

    @BeforeEach
    fun setUp() {
        idSeq = 1L
        service = CsatSurveyService(
            surveyRepository = surveyRepository,
            followUpRepository = followUpRepository,
            ticketRepository = ticketRepository,
            messageLogRepository = messageLogRepository,
            whatsAppService = whatsAppService,
            serviceWindowService = serviceWindowService,
            eventPublisher = eventPublisher,
            properties = props
        )
        every { surveyRepository.save(any()) } answers {
            val s = firstArg<CsatSurvey>()
            if (s.id == null) s.id = idSeq++
            s
        }
        every { followUpRepository.save(any()) } answers {
            val f = firstArg<CsatFollowUp>()
            if (f.id == null) f.id = idSeq++
            f
        }
        every { messageLogRepository.save(any()) } answers { firstArg() }
        every { whatsAppService.sendTextMessage(any(), any()) } returns
            WhatsAppSendResult(true, "{}", "wamid.thanks", "999111222", "1")
    }

    @Test
    fun `scheduleOnTicketClose creates unique survey once`() {
        every { surveyRepository.findByTicketId(11) } returnsMany listOf(null, survey(ticketId = 11, id = 1L))

        val first = service.scheduleOnTicketClose(ticket(id = 11))
        val second = service.scheduleOnTicketClose(ticket(id = 11))

        assertEquals(CsatSurveyStatus.SCHEDULED, first.status)
        assertEquals(first.id, second.id)
        verify(exactly = 1) { surveyRepository.save(match { it.ticketId == 11 && it.status == CsatSurveyStatus.SCHEDULED }) }
    }

    @Test
    fun `processDueSends uses interactive list inside 24h window`() {
        val due = survey(id = 5L, ticketId = 11, status = CsatSurveyStatus.SCHEDULED)
        every { surveyRepository.findDueForSend(any(), any()) } returns listOf(due)
        every { surveyRepository.findExpiredCandidates(any(), any()) } returns emptyList()
        every { serviceWindowService.getServiceWindow("999111222") } returns
            WhatsAppServiceWindowService.WhatsAppServiceWindowStatus(
                phone = "999111222",
                open = true,
                expiresAt = LocalDateTime.now().plusHours(12)
            )
        every {
            whatsAppService.sendInteractiveListMessage(any(), any(), any(), any(), any())
        } returns WhatsAppSendResult(true, "{}", "wamid.csat1", "999111222", "1")

        service.processDueSurveys()

        assertEquals(CsatSurveyStatus.SENT, due.status)
        assertEquals(CsatSendChannel.INTERACTIVE, due.sendChannel)
        assertEquals("wamid.csat1", due.metaMessageId)
        verify(exactly = 1) {
            whatsAppService.sendInteractiveListMessage(
                phoneNumber = "999111222",
                bodyText = match { it.contains("#11") },
                buttonText = any(),
                sectionTitle = any(),
                rows = match { it.size == 5 }
            )
        }
        verify(exactly = 0) { whatsAppService.sendTemplateMessageWithMetaResponse(any(), any(), any(), any()) }
    }

    @Test
    fun `processDueSends uses template outside window and retries do not create second survey`() {
        val due = survey(id = 5L, ticketId = 11, status = CsatSurveyStatus.SCHEDULED)
        every { surveyRepository.findDueForSend(any(), any()) } returns listOf(due)
        every { surveyRepository.findExpiredCandidates(any(), any()) } returns emptyList()
        every { serviceWindowService.getServiceWindow("999111222") } returns
            WhatsAppServiceWindowService.WhatsAppServiceWindowStatus(
                phone = "999111222",
                open = false,
                expiresAt = null
            )
        every {
            whatsAppService.sendTemplateMessageWithMetaResponse(any(), any(), any(), any())
        } returns WhatsAppSendResult(true, "{}", "wamid.tpl", "999111222", "1")

        service.processDueSurveys()

        assertEquals(CsatSurveyStatus.SENT, due.status)
        assertEquals(CsatSendChannel.TEMPLATE, due.sendChannel)
        verify(exactly = 1) {
            whatsAppService.sendTemplateMessageWithMetaResponse(
                phoneNumber = "999111222",
                templateName = "csat_survey_v1",
                languageCode = "es_PE",
                parameters = any()
            )
        }
    }

    @Test
    fun `failed send increments retries without duplicating survey`() {
        val due = survey(id = 5L, ticketId = 11, status = CsatSurveyStatus.SCHEDULED)
        every { surveyRepository.findDueForSend(any(), any()) } returns listOf(due)
        every { surveyRepository.findExpiredCandidates(any(), any()) } returns emptyList()
        every { serviceWindowService.getServiceWindow(any()) } returns
            WhatsAppServiceWindowService.WhatsAppServiceWindowStatus("999111222", true, LocalDateTime.now().plusHours(1))
        every {
            whatsAppService.sendInteractiveListMessage(any(), any(), any(), any(), any())
        } throws RuntimeException("meta down")

        service.processDueSurveys()

        assertEquals(CsatSurveyStatus.SCHEDULED, due.status)
        assertEquals(1, due.retries)
        assertNotNull(due.nextAttemptAt)
        assertNotNull(due.lastError)
    }

    @Test
    fun `expire marks non answered surveys`() {
        val expired = survey(id = 9L, status = CsatSurveyStatus.SENT).apply {
            expiresAt = LocalDateTime.now().minusMinutes(1)
        }
        every { surveyRepository.findDueForSend(any(), any()) } returns emptyList()
        every { surveyRepository.findExpiredCandidates(any(), any()) } returns listOf(expired)

        service.processDueSurveys()

        assertEquals(CsatSurveyStatus.EXPIRED, expired.status)
    }

    @Test
    fun `capture score is idempotent by meta message id`() {
        val sent = survey(id = 7L, ticketId = 22, status = CsatSurveyStatus.SENT)
        every { surveyRepository.findByCaptureIdempotencyKey("wamid.in1") } returnsMany listOf(null, sent)
        every { surveyRepository.findById(7L) } returns Optional.of(sent)
        every { followUpRepository.findBySurveyId(7L) } returns null

        val first = service.captureScoreReply(
            surveyId = 7L,
            score = 5,
            metaMessageId = "wamid.in1",
            phone = "999111222"
        )
        sent.status = CsatSurveyStatus.ANSWERED
        sent.score = 5
        sent.captureIdempotencyKey = "wamid.in1"
        val second = service.captureScoreReply(
            surveyId = 7L,
            score = 1,
            metaMessageId = "wamid.in1",
            phone = "999111222"
        )

        assertEquals(5, first?.score)
        assertEquals(5, second?.score)
        verify(exactly = 1) { surveyRepository.save(match { it.score == 5 && it.status == CsatSurveyStatus.ANSWERED }) }
    }

    @Test
    fun `low score creates follow up once and publishes alert without reopening ticket`() {
        val sent = survey(id = 7L, ticketId = 22, status = CsatSurveyStatus.SENT)
        every { surveyRepository.findByCaptureIdempotencyKey("wamid.low") } returns null
        every { surveyRepository.findById(7L) } returns Optional.of(sent)
        every { followUpRepository.findBySurveyId(7L) } returnsMany listOf(null, followUp(surveyId = 7L))
        every {
            whatsAppService.sendInteractiveListMessage(any(), any(), any(), any(), any())
        } returns WhatsAppSendResult(true, "{}", "wamid.reason", "999111222", "1")
        every { ticketRepository.findById(22) } returns Optional.of(ticket(id = 22, status = AssistanceTicketStatus.CLOSED))

        service.captureScoreReply(
            surveyId = 7L,
            score = 2,
            metaMessageId = "wamid.low",
            phone = "999111222"
        )

        verify(exactly = 1) { followUpRepository.save(match { it.surveyId == 7L && it.status == CsatFollowUpStatus.OPEN }) }
        verify { eventPublisher.publish(CsatSurveyService.EVENT_LOW_SCORE, any()) }
        verify(exactly = 0) { ticketRepository.save(any()) }
    }

    @Test
    fun `tryHandleInbound captures score button and optional reason`() {
        val sent = survey(id = 3L, ticketId = 8, status = CsatSurveyStatus.SENT)
        every { surveyRepository.findByCaptureIdempotencyKey("wamid.btn") } returns null
        every { surveyRepository.findById(3L) } returns Optional.of(sent)
        every { followUpRepository.findBySurveyId(3L) } returns null

        val handled = service.tryHandleInbound(
            phone = "999111222",
            messageType = "button_reply",
            buttonReplyId = "csat_s_3_4",
            messageText = null,
            metaMessageId = "wamid.btn"
        )

        assertTrue(handled)
        assertEquals(4, sent.score)
        assertEquals(CsatSurveyStatus.ANSWERED, sent.status)
    }

    @Test
    fun `tryHandleInbound captures dissatisfaction reason without second follow up`() {
        val answered = survey(id = 3L, ticketId = 8, status = CsatSurveyStatus.ANSWERED, score = 1)
        val existing = followUp(id = 50L, surveyId = 3L)
        every { surveyRepository.findById(3L) } returns Optional.of(answered)
        every { followUpRepository.findBySurveyId(3L) } returns existing
        every { surveyRepository.findByCaptureIdempotencyKey(any()) } returns null

        val handled = service.tryHandleInbound(
            phone = "999111222",
            messageType = "button_reply",
            buttonReplyId = "csat_r_3_TRATO",
            messageText = null,
            metaMessageId = "wamid.reason1"
        )

        assertTrue(handled)
        assertEquals(CsatDissatisfactionReason.TRATO, answered.dissatisfactionReason)
        assertEquals(CsatDissatisfactionReason.TRATO, existing.reason)
        verify(exactly = 0) { followUpRepository.save(match { it.id == null }) }
    }

    @Test
    fun `summary computes average response rate and sample sizes`() {
        val from = LocalDateTime.of(2026, 8, 1, 0, 0)
        val to = LocalDateTime.of(2026, 8, 3, 0, 0)
        every { surveyRepository.findByScheduledAtBetween(from, to) } returns listOf(
            survey(id = 1, ticketId = 1, status = CsatSurveyStatus.ANSWERED, score = 5, technicianId = 9, placeName = "Norte", ticketCategory = "Fibra"),
            survey(id = 2, ticketId = 2, status = CsatSurveyStatus.ANSWERED, score = 3, technicianId = 9, placeName = "Norte", ticketCategory = "Fibra"),
            survey(id = 3, ticketId = 3, status = CsatSurveyStatus.SENT, technicianId = 9, placeName = "Sur", ticketCategory = "Wifi"),
            survey(id = 4, ticketId = 4, status = CsatSurveyStatus.EXPIRED, placeName = "Sur", ticketCategory = "Wifi")
        )

        val summary = service.buildSummary(from, to)

        assertEquals(4, summary.sent + summary.answered + summary.expired + summary.scheduled + summary.failed)
        assertEquals(2, summary.answered)
        assertEquals(0.5, summary.responseRate, 0.001)
        assertEquals(4.0, summary.averageScore)
        assertEquals(2, summary.byTechnician.first { it.key == "9" }.sampleSize)
        assertEquals(2, summary.byPlace.first { it.key == "Norte" }.sampleSize)
        assertEquals(2, summary.byCategory.first { it.key == "Fibra" }.sampleSize)
    }

    private fun ticket(
        id: Int,
        status: AssistanceTicketStatus = AssistanceTicketStatus.CLOSED,
        phone: String = "999111222"
    ): AssistanceTicket {
        val tech = User(id = 42, name = "Tech", lastName = "One")
        return AssistanceTicket(
            id = id,
            phone = phone,
            category = "Sin Conexión a Internet",
            description = "test",
            status = status,
            placeName = "Zona Norte",
            responsible = tech
        )
    }

    private fun survey(
        id: Long? = null,
        ticketId: Int = 11,
        status: CsatSurveyStatus = CsatSurveyStatus.SCHEDULED,
        score: Int? = null,
        technicianId: Int? = 42,
        placeName: String? = "Zona Norte",
        ticketCategory: String? = "Sin Conexión a Internet"
    ): CsatSurvey {
        val now = LocalDateTime.now()
        return CsatSurvey(
            id = id,
            ticketId = ticketId,
            phone = "999111222",
            status = status,
            score = score,
            technicianId = technicianId,
            placeName = placeName,
            ticketCategory = ticketCategory,
            sendIdempotencyKey = "CSAT-SEND-$ticketId-0",
            retries = 0,
            maxRetries = 3,
            nextAttemptAt = now,
            scheduledAt = now,
            expiresAt = now.plusHours(72),
            createdAt = now,
            updatedAt = now
        )
    }

    private fun followUp(id: Long? = null, surveyId: Long): CsatFollowUp {
        val now = LocalDateTime.now()
        return CsatFollowUp(
            id = id,
            surveyId = surveyId,
            status = CsatFollowUpStatus.OPEN,
            createdAt = now,
            updatedAt = now
        )
    }
}
