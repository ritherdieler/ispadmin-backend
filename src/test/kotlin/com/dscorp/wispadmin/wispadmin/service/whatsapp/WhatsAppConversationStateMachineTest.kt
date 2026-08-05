package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppConversationStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhatsAppConversationStateMachineTest {

    private val machine = WhatsAppConversationStateMachine

    @Test
    fun `main menu button opens support menu`() {
        val transition = machine.next(
            WhatsAppConversationStep.MAIN_MENU,
            WhatsAppBotEvent.ButtonSelected(WhatsAppBotMenuCatalog.REPORT_FAULT)
        )

        assertEquals(WhatsAppBotAction.SHOW_SUPPORT_MENU, transition.action)
        assertEquals(WhatsAppConversationStep.SUPPORT_MENU, transition.nextStep)
        assertNull(transition.handoffReason)
    }

    @Test
    fun `payment proof button waits for image or pdf`() {
        val transition = machine.next(
            WhatsAppConversationStep.MAIN_MENU,
            WhatsAppBotEvent.ButtonSelected(WhatsAppBotMenuCatalog.PAYMENT_PROOF)
        )

        assertEquals(WhatsAppBotAction.REQUEST_PAYMENT_PROOF, transition.action)
        assertEquals(WhatsAppConversationStep.AWAITING_PAYMENT_PROOF, transition.nextStep)
    }

    @Test
    fun `paid button shows account status and waits for proof`() {
        val transition = machine.next(
            WhatsAppConversationStep.DEBT_VIEW,
            WhatsAppBotEvent.ButtonSelected(WhatsAppBotMenuCatalog.PAID)
        )

        assertEquals(WhatsAppBotAction.SHOW_PAID_STATUS, transition.action)
        assertEquals(WhatsAppConversationStep.AWAITING_PAYMENT_PROOF, transition.nextStep)
    }

    @Test
    fun `proof received while waiting confirms and returns to main menu`() {
        val transition = machine.next(
            WhatsAppConversationStep.AWAITING_PAYMENT_PROOF,
            WhatsAppBotEvent.PaymentProofReceived
        )

        assertEquals(WhatsAppBotAction.CONFIRM_PAYMENT_PROOF, transition.action)
        assertEquals(WhatsAppConversationStep.MAIN_MENU, transition.nextStep)
        assertFalse(transition.action.sendsInteractiveMenu)
    }

    @Test
    fun `proof received in any other step keeps current step`() {
        listOf(
            WhatsAppConversationStep.MAIN_MENU,
            WhatsAppConversationStep.SUPPORT_DIAG,
            WhatsAppConversationStep.ESPERANDO_ASESOR
        ).forEach { step ->
            val transition = machine.next(step, WhatsAppBotEvent.PaymentProofReceived)

            assertEquals(WhatsAppBotAction.CONFIRM_PAYMENT_PROOF, transition.action)
            assertNull(transition.nextStep, "step $step no debe cambiar al recibir comprobante")
        }
    }

    @Test
    fun `unmatched text while waiting for proof reminds instead of generic guardrail`() {
        val waiting = machine.next(
            WhatsAppConversationStep.AWAITING_PAYMENT_PROOF,
            WhatsAppBotEvent.UnmatchedText
        )
        val menu = machine.next(
            WhatsAppConversationStep.MAIN_MENU,
            WhatsAppBotEvent.UnmatchedText
        )

        assertEquals(WhatsAppBotAction.REMIND_PAYMENT_PROOF, waiting.action)
        assertNull(waiting.nextStep)
        assertEquals(WhatsAppBotAction.INVALID_SELECTION, menu.action)
    }

    @Test
    fun `back from diagnostic returns to support menu and from other steps to main menu`() {
        val fromDiagnostic = machine.next(
            WhatsAppConversationStep.SUPPORT_DIAG,
            WhatsAppBotEvent.ButtonSelected(WhatsAppBotMenuCatalog.BACK)
        )
        val fromProof = machine.next(
            WhatsAppConversationStep.AWAITING_PAYMENT_PROOF,
            WhatsAppBotEvent.ButtonSelected(WhatsAppBotMenuCatalog.BACK)
        )

        assertEquals(WhatsAppBotAction.SHOW_SUPPORT_MENU, fromDiagnostic.action)
        assertEquals(WhatsAppConversationStep.SUPPORT_MENU, fromDiagnostic.nextStep)
        assertEquals(WhatsAppBotAction.SHOW_MAIN_MENU, fromProof.action)
        assertEquals(WhatsAppConversationStep.MAIN_MENU, fromProof.nextStep)
    }

    @Test
    fun `support issue button opens diagnostic and diagnostic answer escalates`() {
        val issue = machine.next(
            WhatsAppConversationStep.SUPPORT_MENU,
            WhatsAppBotEvent.ButtonSelected("${WhatsAppBotMenuCatalog.SUPPORT_ISSUE_PREFIX}no_internet")
        )
        val diagnostic = machine.next(
            WhatsAppConversationStep.SUPPORT_DIAG,
            WhatsAppBotEvent.ButtonSelected("${WhatsAppBotMenuCatalog.SUPPORT_DIAG_PREFIX}fiber_red")
        )

        assertEquals(WhatsAppBotAction.SHOW_SUPPORT_DIAGNOSTIC, issue.action)
        assertEquals(WhatsAppConversationStep.SUPPORT_DIAG, issue.nextStep)
        assertEquals(WhatsAppBotAction.CLOSE_SUPPORT_DIAGNOSTIC, diagnostic.action)
        assertEquals(WhatsAppConversationStep.ESPERANDO_ASESOR, diagnostic.nextStep)
        assertEquals("support_diagnostic", diagnostic.handoffReason)
    }

    @Test
    fun `advisor button escalates with handoff reason`() {
        val transition = machine.next(
            WhatsAppConversationStep.DEBT_VIEW,
            WhatsAppBotEvent.ButtonSelected(WhatsAppBotMenuCatalog.ADVISOR)
        )

        assertEquals(WhatsAppBotAction.ESCALATE_TO_ADVISOR, transition.action)
        assertEquals(WhatsAppConversationStep.ESPERANDO_ASESOR, transition.nextStep)
        assertEquals("advisor_request", transition.handoffReason)
    }

    @Test
    fun `unknown or null callback is rejected without changing step`() {
        listOf(null, "callback_antiguo").forEach { buttonId ->
            val transition = machine.next(
                WhatsAppConversationStep.MAIN_MENU,
                WhatsAppBotEvent.ButtonSelected(buttonId)
            )

            assertEquals(WhatsAppBotAction.INVALID_SELECTION, transition.action)
            assertNull(transition.nextStep)
        }
    }

    @Test
    fun `interactive menu actions persist their own step`() {
        assertTrue(WhatsAppBotAction.SHOW_MAIN_MENU.sendsInteractiveMenu)
        assertTrue(WhatsAppBotAction.REQUEST_PAYMENT_PROOF.sendsInteractiveMenu)
        assertFalse(WhatsAppBotAction.ESCALATE_TO_ADVISOR.sendsInteractiveMenu)
        assertFalse(WhatsAppBotAction.INVALID_SELECTION.sendsInteractiveMenu)
    }
}
