package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppAutoReplyProperties
import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppChatState
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppChatStatus
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppConversationStep
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppChatStateRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class WhatsAppChatStateServiceTest {

    private val repository = mockk<WhatsAppChatStateRepository>()
    private val properties = WhatsAppProperties().apply {
        autoReply = WhatsAppAutoReplyProperties().apply {
            sessionTimeoutMinutes = 10
            advisorWaitTimeoutMinutes = 30
        }
    }

    private lateinit var service: WhatsAppChatStateService

    @BeforeEach
    fun setUp() {
        service = WhatsAppChatStateService(repository, properties)
    }

    @Test
    fun `expired client starts initial greeting session and moves to main menu`() {
        val phone = "51902354183"
        val previous = WhatsAppChatState(
            phone = phone,
            status = WhatsAppChatStatus.BOT_ACTIVE,
            currentStep = WhatsAppConversationStep.SUPPORT_MENU,
            botPaused = false,
            lastInteractionAt = LocalDateTime.now().minusMinutes(11)
        )
        val savedSlot = slot<WhatsAppChatState>()
        every { repository.findByPhone(phone) } returns previous
        every { repository.save(capture(savedSlot)) } answers { firstArg() }

        val session = service.beginInboundInteraction(phone)

        assertFalse(session.botPaused)
        assertTrue(session.isNewOrExpired)
        assertEquals(WhatsAppConversationStep.MAIN_MENU, session.currentStep)
        assertEquals(WhatsAppConversationStep.MAIN_MENU, savedSlot.captured.currentStep)
        assertFalse(savedSlot.captured.botPaused)
    }

    @Test
    fun `active client does not trigger initial greeting and keeps current step`() {
        val phone = "51902354183"
        val previous = WhatsAppChatState(
            phone = phone,
            status = WhatsAppChatStatus.BOT_ACTIVE,
            currentStep = WhatsAppConversationStep.SUPPORT_MENU,
            botPaused = false,
            lastInteractionAt = LocalDateTime.now().minusMinutes(2)
        )
        val savedSlot = slot<WhatsAppChatState>()
        every { repository.findByPhone(phone) } returns previous
        every { repository.save(capture(savedSlot)) } answers { firstArg() }

        val session = service.beginInboundInteraction(phone)

        assertFalse(session.botPaused)
        assertFalse(session.isNewOrExpired)
        assertEquals(WhatsAppConversationStep.SUPPORT_MENU, session.currentStep)
        assertEquals(WhatsAppConversationStep.SUPPORT_MENU, savedSlot.captured.currentStep)
    }

    @Test
    fun `paused client ignores interactions without refreshing session`() {
        val phone = "51902354183"
        val previous = WhatsAppChatState(
            phone = phone,
            status = WhatsAppChatStatus.ESPERANDO_ASESOR,
            currentStep = WhatsAppConversationStep.ESPERANDO_ASESOR,
            botPaused = true,
            lastInteractionAt = LocalDateTime.now().minusMinutes(20),
            updatedAt = LocalDateTime.now().minusMinutes(20)
        )
        every { repository.findByPhone(phone) } returns previous

        val session = service.beginInboundInteraction(phone)

        assertTrue(session.botPaused)
        assertFalse(session.isNewOrExpired)
        assertFalse(session.autoResumedFromAdvisorWait)
        assertEquals(WhatsAppConversationStep.ESPERANDO_ASESOR, session.currentStep)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `paused client under advisor wait timeout stays paused`() {
        val phone = "51902354183"
        val previous = WhatsAppChatState(
            phone = phone,
            status = WhatsAppChatStatus.ESPERANDO_ASESOR,
            currentStep = WhatsAppConversationStep.ESPERANDO_ASESOR,
            botPaused = true,
            lastInteractionAt = LocalDateTime.now().minusMinutes(29),
            updatedAt = LocalDateTime.now().minusMinutes(29)
        )
        every { repository.findByPhone(phone) } returns previous

        val session = service.beginInboundInteraction(phone)

        assertTrue(session.botPaused)
        assertFalse(session.isNewOrExpired)
        assertFalse(session.autoResumedFromAdvisorWait)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `paused client after advisor wait timeout auto resumes and starts main menu session`() {
        val phone = "51902354183"
        val previous = WhatsAppChatState(
            phone = phone,
            status = WhatsAppChatStatus.ESPERANDO_ASESOR,
            currentStep = WhatsAppConversationStep.ESPERANDO_ASESOR,
            botPaused = true,
            metadata = "support_diagnostic",
            lastInteractionAt = LocalDateTime.now().minusMinutes(40),
            updatedAt = LocalDateTime.now().minusMinutes(31)
        )
        val savedSlot = slot<WhatsAppChatState>()
        every { repository.findByPhone(phone) } returns previous
        every { repository.save(capture(savedSlot)) } answers { firstArg() }

        val session = service.beginInboundInteraction(phone)

        assertFalse(session.botPaused)
        assertTrue(session.isNewOrExpired)
        assertTrue(session.autoResumedFromAdvisorWait)
        assertEquals(WhatsAppConversationStep.MAIN_MENU, session.currentStep)
        assertEquals(WhatsAppChatStatus.BOT_ACTIVE, savedSlot.captured.status)
        assertEquals(WhatsAppConversationStep.MAIN_MENU, savedSlot.captured.currentStep)
        assertFalse(savedSlot.captured.botPaused)
        assertTrue(savedSlot.captured.metadata?.contains("auto_resume_advisor_wait") == true)
    }

    @Test
    fun `receipt review step is not treated as pending interactive menu`() {
        val phone = "51902354183"
        every { repository.findByPhone(phone) } returns WhatsAppChatState(
            phone = phone,
            status = WhatsAppChatStatus.BOT_ACTIVE,
            currentStep = WhatsAppConversationStep.AWAITING_RECEIPT_REVIEW,
            botPaused = false,
            lastInteractionAt = LocalDateTime.now()
        )

        assertFalse(service.hasPendingInteractiveMenu(phone))
    }

    @Test
    fun `awaiting payment proof is treated as pending interactive menu`() {
        val phone = "51902354183"
        every { repository.findByPhone(phone) } returns WhatsAppChatState(
            phone = phone,
            status = WhatsAppChatStatus.BOT_ACTIVE,
            currentStep = WhatsAppConversationStep.AWAITING_PAYMENT_PROOF,
            botPaused = false,
            lastInteractionAt = LocalDateTime.now()
        )

        assertTrue(service.hasPendingInteractiveMenu(phone))
    }
}
