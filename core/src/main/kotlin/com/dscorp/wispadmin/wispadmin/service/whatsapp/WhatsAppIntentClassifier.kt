package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.CrmLlmProperties
import org.springframework.stereotype.Service

data class IntentClassification(
    val intent: WhatsAppInboundIntent,
    val confidence: Double,
    val source: String,
    val escalate: Boolean = false,
    val escalateReason: String? = null
)

@Service
class WhatsAppIntentClassifier(
    private val rulesRouter: WhatsAppInboundIntentRouter,
    private val llmClient: LlmClient,
    private val chatStateService: WhatsAppChatStateService,
    private val auditService: WhatsAppAuditService,
    private val properties: CrmLlmProperties
) {

    fun classify(phone: String, messageText: String?): IntentClassification {
        val rulesIntent = rulesRouter.route(messageText)
        if (rulesIntent == WhatsAppInboundIntent.HUMAN_ESCALATION) {
            return traced(
                phone = phone,
                classification = IntentClassification(
                    intent = rulesIntent,
                    confidence = 1.0,
                    source = SOURCE_RULES,
                    escalate = true,
                    escalateReason = "frustration_or_human_request"
                )
            )
        }
        if (rulesIntent != WhatsAppInboundIntent.UNKNOWN) {
            chatStateService.resetUnknownRetryCount(phone)
            return traced(
                phone = phone,
                classification = IntentClassification(
                    intent = rulesIntent,
                    confidence = 0.95,
                    source = SOURCE_RULES
                )
            )
        }

        val llm = llmClient.classifyIntent(messageText.orEmpty())
        if (llm != null && llm.confidence >= properties.confidenceThreshold) {
            chatStateService.resetUnknownRetryCount(phone)
            return traced(
                phone = phone,
                classification = IntentClassification(
                    intent = llm.intent,
                    confidence = llm.confidence,
                    source = SOURCE_LLM
                )
            )
        }

        val retries = chatStateService.incrementUnknownRetryCount(phone)
        if (retries >= properties.maxUnknownRetries) {
            chatStateService.resetUnknownRetryCount(phone)
            return traced(
                phone = phone,
                classification = IntentClassification(
                    intent = WhatsAppInboundIntent.HUMAN_ESCALATION,
                    confidence = llm?.confidence ?: 0.0,
                    source = SOURCE_FALLBACK,
                    escalate = true,
                    escalateReason = "low_confidence_retries"
                )
            )
        }

        return traced(
            phone = phone,
            classification = IntentClassification(
                intent = WhatsAppInboundIntent.UNKNOWN,
                confidence = llm?.confidence ?: 0.0,
                source = SOURCE_FALLBACK
            )
        )
    }

    private fun traced(phone: String, classification: IntentClassification): IntentClassification {
        auditService.recordBotTrace(
            phone = phone,
            intent = classification.intent.name,
            source = classification.source,
            confidence = classification.confidence,
            escalateReason = classification.escalateReason
        )
        return classification
    }

    companion object {
        const val SOURCE_RULES = "RULES"
        const val SOURCE_LLM = "LLM"
        const val SOURCE_FALLBACK = "FALLBACK"
    }
}
