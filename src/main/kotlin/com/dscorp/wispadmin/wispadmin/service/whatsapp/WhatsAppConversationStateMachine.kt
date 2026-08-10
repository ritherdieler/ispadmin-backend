package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppConversationStep

/**
 * Acciones que el bot puede ejecutar como resultado de una transición.
 * `sendsInteractiveMenu` indica que el envío del menú persiste el paso por sí mismo,
 * por lo que el orquestador no debe volver a escribir `nextStep`.
 */
enum class WhatsAppBotAction(val sendsInteractiveMenu: Boolean) {
    SHOW_MAIN_MENU(true),
    SHOW_SUPPORT_MENU(true),
    SHOW_SUPPORT_DIAGNOSTIC(true),
    CLOSE_SUPPORT_DIAGNOSTIC(false),
    SHOW_DEBT_MENU(true),
    SHOW_PAID_STATUS(true),
    REQUEST_PAYMENT_PROOF(true),
    CONFIRM_PAYMENT_PROOF(false),
    REMIND_PAYMENT_PROOF(false),
    ACK_RECEIPT_PENDING(false),
    ESCALATE_TO_ADVISOR(false),
    INVALID_SELECTION(false)
}

sealed interface WhatsAppBotEvent {
    data class ButtonSelected(val buttonId: String?) : WhatsAppBotEvent
    object PaymentProofReceived : WhatsAppBotEvent
    object UnmatchedText : WhatsAppBotEvent
}

data class WhatsAppBotTransition(
    val action: WhatsAppBotAction,
    val nextStep: WhatsAppConversationStep? = null,
    val handoffReason: String? = null
)

/**
 * Tabla explícita de transiciones del bot de WhatsApp: (paso actual, evento) -> acción + paso siguiente.
 * Los callbacks de Meta siguen siendo válidos aunque el paso persistido haya caducado,
 * porque los botones antiguos permanecen pulsables en el chat del cliente.
 */
object WhatsAppConversationStateMachine {

    fun next(
        currentStep: WhatsAppConversationStep,
        event: WhatsAppBotEvent
    ): WhatsAppBotTransition = when (event) {
        is WhatsAppBotEvent.ButtonSelected -> onButton(currentStep, event.buttonId)
        WhatsAppBotEvent.PaymentProofReceived -> onPaymentProof(currentStep)
        WhatsAppBotEvent.UnmatchedText -> onUnmatchedText(currentStep)
    }

    private fun onButton(
        currentStep: WhatsAppConversationStep,
        buttonId: String?
    ): WhatsAppBotTransition = when {
        buttonId == WhatsAppBotMenuCatalog.HOME -> WhatsAppBotTransition(
            action = WhatsAppBotAction.SHOW_MAIN_MENU,
            nextStep = WhatsAppConversationStep.MAIN_MENU
        )

        buttonId == WhatsAppBotMenuCatalog.BACK -> when (currentStep) {
            WhatsAppConversationStep.SUPPORT_DIAG -> WhatsAppBotTransition(
                action = WhatsAppBotAction.SHOW_SUPPORT_MENU,
                nextStep = WhatsAppConversationStep.SUPPORT_MENU
            )
            else -> WhatsAppBotTransition(
                action = WhatsAppBotAction.SHOW_MAIN_MENU,
                nextStep = WhatsAppConversationStep.MAIN_MENU
            )
        }

        buttonId == WhatsAppBotMenuCatalog.REPORT_FAULT ||
            buttonId == WhatsAppBotMenuCatalog.SUPPORT_LEGACY -> WhatsAppBotTransition(
            action = WhatsAppBotAction.SHOW_SUPPORT_MENU,
            nextStep = WhatsAppConversationStep.SUPPORT_MENU
        )

        WhatsAppBotMenuCatalog.isSupportIssue(buttonId) -> WhatsAppBotTransition(
            action = WhatsAppBotAction.SHOW_SUPPORT_DIAGNOSTIC,
            nextStep = WhatsAppConversationStep.SUPPORT_DIAG
        )

        WhatsAppBotMenuCatalog.isSupportDiagnostic(buttonId) -> WhatsAppBotTransition(
            action = WhatsAppBotAction.CLOSE_SUPPORT_DIAGNOSTIC,
            nextStep = WhatsAppConversationStep.ESPERANDO_ASESOR,
            handoffReason = "support_diagnostic"
        )

        buttonId == WhatsAppBotMenuCatalog.DEBT -> WhatsAppBotTransition(
            action = WhatsAppBotAction.SHOW_DEBT_MENU,
            nextStep = WhatsAppConversationStep.DEBT_VIEW
        )

        buttonId == WhatsAppBotMenuCatalog.PAID -> WhatsAppBotTransition(
            action = WhatsAppBotAction.SHOW_PAID_STATUS,
            nextStep = WhatsAppConversationStep.AWAITING_PAYMENT_PROOF
        )

        buttonId == WhatsAppBotMenuCatalog.PAYMENT_PROOF -> WhatsAppBotTransition(
            action = WhatsAppBotAction.REQUEST_PAYMENT_PROOF,
            nextStep = WhatsAppConversationStep.AWAITING_PAYMENT_PROOF
        )

        buttonId == WhatsAppBotMenuCatalog.ADVISOR -> WhatsAppBotTransition(
            action = WhatsAppBotAction.ESCALATE_TO_ADVISOR,
            nextStep = WhatsAppConversationStep.ESPERANDO_ASESOR,
            handoffReason = "advisor_request"
        )

        buttonId == WhatsAppBotMenuCatalog.INSTALLATION_LEGACY -> WhatsAppBotTransition(
            action = WhatsAppBotAction.ESCALATE_TO_ADVISOR,
            nextStep = WhatsAppConversationStep.ESPERANDO_ASESOR,
            handoffReason = "installation_request"
        )

        else -> WhatsAppBotTransition(action = WhatsAppBotAction.INVALID_SELECTION)
    }

    private fun onPaymentProof(currentStep: WhatsAppConversationStep): WhatsAppBotTransition {
        val nextStep = if (currentStep == WhatsAppConversationStep.ESPERANDO_ASESOR) {
            null
        } else {
            WhatsAppConversationStep.AWAITING_RECEIPT_REVIEW
        }
        return WhatsAppBotTransition(
            action = WhatsAppBotAction.CONFIRM_PAYMENT_PROOF,
            nextStep = nextStep
        )
    }

    private fun onUnmatchedText(currentStep: WhatsAppConversationStep): WhatsAppBotTransition =
        when (currentStep) {
            WhatsAppConversationStep.AWAITING_PAYMENT_PROOF -> WhatsAppBotTransition(
                action = WhatsAppBotAction.REMIND_PAYMENT_PROOF
            )
            WhatsAppConversationStep.AWAITING_RECEIPT_REVIEW -> WhatsAppBotTransition(
                action = WhatsAppBotAction.ACK_RECEIPT_PENDING
            )
            else -> WhatsAppBotTransition(action = WhatsAppBotAction.INVALID_SELECTION)
        }
}
