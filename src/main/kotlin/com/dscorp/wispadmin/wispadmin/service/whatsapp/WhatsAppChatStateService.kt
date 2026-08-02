package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppChatState
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppChatStatus
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppConversationStep
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppChatStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.time.LocalDateTime

data class WhatsAppInboundSession(
    val botPaused: Boolean,
    val isNewOrExpired: Boolean,
    val lastInteractionAt: LocalDateTime?,
    val currentStep: WhatsAppConversationStep
)

@Service
class WhatsAppChatStateService(
    private val chatStateRepository: WhatsAppChatStateRepository,
    private val whatsAppProperties: WhatsAppProperties
) {

    fun isBotPaused(phone: String): Boolean {
        val state = chatStateRepository.findByPhone(normalizePhone(phone)) ?: return false
        return isPaused(state)
    }

    fun beginInboundInteraction(phone: String): WhatsAppInboundSession {
        val normalized = normalizePhone(phone)
        val now = LocalDateTime.now()
        val current = chatStateRepository.findByPhone(normalized)

        if (current != null && isPaused(current)) {
            return WhatsAppInboundSession(
                botPaused = true,
                isNewOrExpired = false,
                lastInteractionAt = current.lastInteractionAt,
                currentStep = WhatsAppConversationStep.ESPERANDO_ASESOR
            )
        }

        val previousInteraction = current?.lastInteractionAt ?: current?.updatedAt
        val timeoutMinutes = whatsAppProperties.autoReply.sessionTimeoutMinutes.coerceAtLeast(1).toLong()
        val expired = previousInteraction == null ||
            previousInteraction.isBefore(now.minusMinutes(timeoutMinutes))

        val nextStep = if (expired) {
            WhatsAppConversationStep.MAIN_MENU
        } else {
            current?.currentStep ?: WhatsAppConversationStep.MAIN_MENU
        }

        val state = current?.copy(
            status = WhatsAppChatStatus.BOT_ACTIVE,
            currentStep = nextStep,
            botPaused = false,
            lastInteractionAt = now,
            updatedAt = now
        ) ?: WhatsAppChatState(
            phone = normalized,
            status = WhatsAppChatStatus.BOT_ACTIVE,
            currentStep = WhatsAppConversationStep.MAIN_MENU,
            botPaused = false,
            lastInteractionAt = now,
            createdAt = now,
            updatedAt = now
        )
        chatStateRepository.save(state)

        return WhatsAppInboundSession(
            botPaused = false,
            isNewOrExpired = expired,
            lastInteractionAt = previousInteraction,
            currentStep = nextStep
        )
    }

    fun markWaitingForAdvisor(phone: String, metadata: String? = null): WhatsAppChatState {
        val normalized = normalizePhone(phone)
        val now = LocalDateTime.now()
        val current = chatStateRepository.findByPhone(normalized)
        val state = current?.copy(
            status = WhatsAppChatStatus.ESPERANDO_ASESOR,
            currentStep = WhatsAppConversationStep.ESPERANDO_ASESOR,
            botPaused = true,
            metadata = metadata?.take(500),
            lastInteractionAt = current.lastInteractionAt ?: now,
            updatedAt = now
        ) ?: WhatsAppChatState(
            phone = normalized,
            status = WhatsAppChatStatus.ESPERANDO_ASESOR,
            currentStep = WhatsAppConversationStep.ESPERANDO_ASESOR,
            botPaused = true,
            metadata = metadata?.take(500),
            lastInteractionAt = now,
            createdAt = now,
            updatedAt = now
        )
        return chatStateRepository.save(state)
    }

    fun resumeBot(phone: String, metadata: String? = null): WhatsAppChatState {
        val normalized = normalizePhone(phone)
        val now = LocalDateTime.now()
        val current = chatStateRepository.findByPhone(normalized)
        val state = current?.copy(
            status = WhatsAppChatStatus.BOT_ACTIVE,
            currentStep = WhatsAppConversationStep.MAIN_MENU,
            botPaused = false,
            metadata = metadata?.take(500),
            lastInteractionAt = now,
            updatedAt = now
        ) ?: WhatsAppChatState(
            phone = normalized,
            status = WhatsAppChatStatus.BOT_ACTIVE,
            currentStep = WhatsAppConversationStep.MAIN_MENU,
            botPaused = false,
            metadata = metadata?.take(500),
            lastInteractionAt = now,
            createdAt = now,
            updatedAt = now
        )
        return chatStateRepository.save(state)
    }

    fun setCurrentStep(
        phone: String,
        currentStep: WhatsAppConversationStep,
        metadata: String? = null
    ): WhatsAppChatState {
        if (currentStep == WhatsAppConversationStep.ESPERANDO_ASESOR) {
            return markWaitingForAdvisor(phone, metadata)
        }

        val normalized = normalizePhone(phone)
        val now = LocalDateTime.now()
        val current = chatStateRepository.findByPhone(normalized)
        val state = current?.copy(
            status = WhatsAppChatStatus.BOT_ACTIVE,
            currentStep = currentStep,
            botPaused = false,
            metadata = metadata?.take(500) ?: current.metadata,
            updatedAt = now
        ) ?: WhatsAppChatState(
            phone = normalized,
            status = WhatsAppChatStatus.BOT_ACTIVE,
            currentStep = currentStep,
            botPaused = false,
            metadata = metadata?.take(500),
            lastInteractionAt = now,
            createdAt = now,
            updatedAt = now
        )
        return chatStateRepository.save(state)
    }

    fun currentStep(phone: String): WhatsAppConversationStep? {
        return chatStateRepository.findByPhone(normalizePhone(phone))?.currentStep
    }

    fun getMetadata(phone: String): String? {
        return chatStateRepository.findByPhone(normalizePhone(phone))?.metadata
    }

    fun hasPendingInteractiveMenu(phone: String): Boolean {
        val state = chatStateRepository.findByPhone(normalizePhone(phone)) ?: return false
        if (isPaused(state)) return false
        return state.currentStep in setOf(
            WhatsAppConversationStep.MAIN_MENU,
            WhatsAppConversationStep.SUPPORT_MENU,
            WhatsAppConversationStep.SUPPORT_DIAG,
            WhatsAppConversationStep.DEBT_VIEW
        )
    }

    fun hasPendingSupportDiagnostic(phone: String): Boolean {
        val state = chatStateRepository.findByPhone(normalizePhone(phone)) ?: return false
        if (isPaused(state)) return false
        return state.currentStep in setOf(
            WhatsAppConversationStep.SUPPORT_MENU,
            WhatsAppConversationStep.SUPPORT_DIAG
        )
    }

    suspend fun markWaitingForAdvisorAsync(phone: String, metadata: String? = null): WhatsAppChatState =
        withContext(Dispatchers.IO) { markWaitingForAdvisor(phone, metadata) }

    suspend fun resumeBotAsync(phone: String, metadata: String? = null): WhatsAppChatState =
        withContext(Dispatchers.IO) { resumeBot(phone, metadata) }

    suspend fun setCurrentStepAsync(
        phone: String,
        currentStep: WhatsAppConversationStep,
        metadata: String? = null
    ): WhatsAppChatState = withContext(Dispatchers.IO) { setCurrentStep(phone, currentStep, metadata) }

    fun getUnknownRetryCount(phone: String): Int {
        val meta = chatStateRepository.findByPhone(normalizePhone(phone))?.metadata ?: return 0
        return UNKNOWN_RETRY_REGEX.find(meta)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    fun incrementUnknownRetryCount(phone: String): Int {
        val normalized = normalizePhone(phone)
        val now = LocalDateTime.now()
        val current = chatStateRepository.findByPhone(normalized)
        val next = getUnknownRetryCount(normalized) + 1
        val baseMeta = current?.metadata
            ?.replace(UNKNOWN_RETRY_REGEX, "")
            ?.trim()
            ?.trim(';')
            .orEmpty()
        val metadata = listOfNotNull(
            baseMeta.takeIf { it.isNotBlank() },
            "$UNKNOWN_RETRY_PREFIX$next"
        ).joinToString(";")
        val state = current?.copy(metadata = metadata.take(500), updatedAt = now)
            ?: WhatsAppChatState(
                phone = normalized,
                metadata = metadata.take(500),
                lastInteractionAt = now,
                createdAt = now,
                updatedAt = now
            )
        chatStateRepository.save(state)
        return next
    }

    fun resetUnknownRetryCount(phone: String) {
        val normalized = normalizePhone(phone)
        val current = chatStateRepository.findByPhone(normalized) ?: return
        val cleaned = current.metadata
            ?.replace(UNKNOWN_RETRY_REGEX, "")
            ?.trim()
            ?.trim(';')
            ?.ifBlank { null }
        if (cleaned == current.metadata) return
        chatStateRepository.save(current.copy(metadata = cleaned, updatedAt = LocalDateTime.now()))
    }

    private fun isPaused(state: WhatsAppChatState): Boolean {
        return state.botPaused ||
            state.status == WhatsAppChatStatus.ESPERANDO_ASESOR ||
            state.currentStep == WhatsAppConversationStep.ESPERANDO_ASESOR
    }

    private fun normalizePhone(phone: String): String = phone.filter { it.isDigit() }

    companion object {
        private const val UNKNOWN_RETRY_PREFIX = "unknown_retries="
        private val UNKNOWN_RETRY_REGEX = Regex("""unknown_retries=(\d+)""")
    }
}
