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
    fun `proof received while waiting confirms and moves to receipt review`() {
        val transition = machine.next(
            WhatsAppConversationStep.AWAITING_PAYMENT_PROOF,
            WhatsAppBotEvent.PaymentProofReceived
        )

        assertEquals(WhatsAppBotAction.CONFIRM_PAYMENT_PROOF, transition.action)
        assertEquals(WhatsAppConversationStep.AWAITING_RECEIPT_REVIEW, transition.nextStep)
        assertFalse(transition.action.sendsInteractiveMenu)
    }

    @Test
    fun `proof received outside advisor wait moves to receipt review`() {
        listOf(
            WhatsAppConversationStep.MAIN_MENU,
            WhatsAppConversationStep.SUPPORT_DIAG,
            WhatsAppConversationStep.DEBT_VIEW
        ).forEach { step ->
            val transition = machine.next(step, WhatsAppBotEvent.PaymentProofReceived)

            assertEquals(WhatsAppBotAction.CONFIRM_PAYMENT_PROOF, transition.action)
            assertEquals(
                WhatsAppConversationStep.AWAITING_RECEIPT_REVIEW,
                transition.nextStep,
                "step $step debe pasar a revision de comprobante"
            )
        }
    }

    @Test
    fun `proof received while waiting for advisor keeps current step`() {
        val transition = machine.next(
            WhatsAppConversationStep.ESPERANDO_ASESOR,
            WhatsAppBotEvent.PaymentProofReceived
        )

        assertEquals(WhatsAppBotAction.CONFIRM_PAYMENT_PROOF, transition.action)
        assertNull(transition.nextStep)
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
        assertEquals(WhatsAppBotAction.SHOW_MAIN_MENU, menu.action)
        assertEquals(WhatsAppConversationStep.MAIN_MENU, menu.nextStep)
    }

    @Test
    fun `unmatched text on menu steps re shows the current menu`() {
        val support = machine.next(
            WhatsAppConversationStep.SUPPORT_MENU,
            WhatsAppBotEvent.UnmatchedText
        )
        val debt = machine.next(
            WhatsAppConversationStep.DEBT_VIEW,
            WhatsAppBotEvent.UnmatchedText
        )
        val diag = machine.next(
            WhatsAppConversationStep.SUPPORT_DIAG,
            WhatsAppBotEvent.UnmatchedText
        )

        assertEquals(WhatsAppBotAction.SHOW_SUPPORT_MENU, support.action)
        assertEquals(WhatsAppConversationStep.SUPPORT_MENU, support.nextStep)
        assertEquals(WhatsAppBotAction.SHOW_DEBT_MENU, debt.action)
        assertEquals(WhatsAppConversationStep.DEBT_VIEW, debt.nextStep)
        assertEquals(WhatsAppBotAction.SHOW_SUPPORT_MENU, diag.action)
        assertEquals(WhatsAppConversationStep.SUPPORT_MENU, diag.nextStep)
    }

    @Test
    fun `unmatched text while awaiting receipt review soft acks without menu`() {
        val transition = machine.next(
            WhatsAppConversationStep.AWAITING_RECEIPT_REVIEW,
            WhatsAppBotEvent.UnmatchedText
        )

        assertEquals(WhatsAppBotAction.ACK_RECEIPT_PENDING, transition.action)
        assertNull(transition.nextStep)
        assertFalse(transition.action.sendsInteractiveMenu)
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
    fun `stale diagnostic button outside SUPPORT_DIAG re shows main menu without handoff`() {
        listOf(
            WhatsAppConversationStep.MAIN_MENU,
            WhatsAppConversationStep.ESPERANDO_ASESOR,
            WhatsAppConversationStep.DEBT_VIEW,
            WhatsAppConversationStep.AWAITING_PAYMENT_PROOF
        ).forEach { step ->
            val transition = machine.next(
                step,
                WhatsAppBotEvent.ButtonSelected("${WhatsAppBotMenuCatalog.SUPPORT_DIAG_PREFIX}fiber_red")
            )

            assertEquals(
                WhatsAppBotAction.SHOW_MAIN_MENU,
                transition.action,
                "step $step no debe cerrar un diagnóstico viejo"
            )
            assertEquals(WhatsAppConversationStep.MAIN_MENU, transition.nextStep)
            assertNull(transition.handoffReason)
        }
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
    fun `unknown or null callback re shows main menu`() {
        listOf(null, "callback_antiguo").forEach { buttonId ->
            val transition = machine.next(
                WhatsAppConversationStep.MAIN_MENU,
                WhatsAppBotEvent.ButtonSelected(buttonId)
            )

            assertEquals(WhatsAppBotAction.SHOW_MAIN_MENU, transition.action)
            assertEquals(WhatsAppConversationStep.MAIN_MENU, transition.nextStep)
        }
    }

    @Test
    fun `interactive menu actions persist their own step`() {
        assertTrue(WhatsAppBotAction.SHOW_MAIN_MENU.sendsInteractiveMenu)
        assertTrue(WhatsAppBotAction.REQUEST_PAYMENT_PROOF.sendsInteractiveMenu)
        assertFalse(WhatsAppBotAction.ESCALATE_TO_ADVISOR.sendsInteractiveMenu)
        assertFalse(WhatsAppBotAction.INVALID_SELECTION.sendsInteractiveMenu)
        assertFalse(WhatsAppBotAction.ACK_RECEIPT_PENDING.sendsInteractiveMenu)
    }
}
