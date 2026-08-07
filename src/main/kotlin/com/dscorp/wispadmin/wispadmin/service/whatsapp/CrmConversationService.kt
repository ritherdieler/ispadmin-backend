package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.CrmAssignmentEvent
import com.dscorp.wispadmin.wispadmin.data.model.CrmAssignmentEventType
import com.dscorp.wispadmin.wispadmin.data.model.CrmChannel
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversation
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversationStatus
import com.dscorp.wispadmin.wispadmin.data.model.CrmInternalNote
import com.dscorp.wispadmin.wispadmin.dto.CrmAssignmentEventDto
import com.dscorp.wispadmin.wispadmin.dto.CrmConversationDto
import com.dscorp.wispadmin.wispadmin.dto.CrmInternalNoteDto
import com.dscorp.wispadmin.wispadmin.dto.CrmSuggestReplyDto
import com.dscorp.wispadmin.wispadmin.repository.CrmAssignmentEventRepository
import com.dscorp.wispadmin.wispadmin.repository.CrmConversationRepository
import com.dscorp.wispadmin.wispadmin.repository.CrmInternalNoteRepository
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class CrmConversationFilter(
    val status: CrmConversationStatus? = null,
    val assignedAgentId: Int? = null,
    val priority: Int? = null,
    val search: String? = null,
    val limit: Int = 100
)

@Service
class CrmConversationService(
    private val conversationRepository: CrmConversationRepository,
    private val assignmentEventRepository: CrmAssignmentEventRepository,
    private val internalNoteRepository: CrmInternalNoteRepository,
    private val userRepository: UserRepository,
    private val crmEventPublisher: CrmEventPublisher,
    private val handoffService: WhatsAppHandoffService,
    private val chatStateService: WhatsAppChatStateService,
    private val llmClient: LlmClient,
    private val inboundMessageRepository: WhatsAppInboundMessageRepository,
    private val messageLogRepository: WhatsAppMessageLogRepository
) {

    fun list(filter: CrmConversationFilter): List<CrmConversationDto> {
        val all = conversationRepository.findAll()
        val search = filter.search?.trim()?.lowercase().orEmpty()
        return all.asSequence()
            .filter { filter.status == null || it.status == filter.status }
            .filter { filter.assignedAgentId == null || it.assignedAgentId == filter.assignedAgentId }
            .filter { filter.priority == null || it.priority == filter.priority }
            .filter {
                if (search.isBlank()) true
                else it.phone.contains(search, ignoreCase = true) ||
                    (it.subscriptionId?.toString()?.contains(search) == true)
            }
            .sortedByDescending { it.lastInboundAt ?: it.updatedAt }
            .take(filter.limit.coerceIn(1, 500))
            .map { it.toDto() }
            .toList()
    }

    fun getById(id: Long): CrmConversationDto = requireConversation(id).toDto()

    fun getByPhone(phone: String): CrmConversationDto? =
        findWhatsAppConversation(phone)?.toDto()

    @Transactional
    fun claim(conversationId: Long, agentId: Int, operatorUsername: String?): CrmConversationDto {
        val existing = requireConversation(conversationId)
        if (existing.assignedAgentId != null) {
            throw CrmConversationConflictException("La conversacion ya esta asignada")
        }
        if (existing.status !in CLAIMABLE_STATUSES) {
            throw CrmConversationConflictException("La conversacion no se puede tomar en estado ${existing.status}")
        }
        val now = LocalDateTime.now()
        val updated = conversationRepository.claimIfUnassigned(conversationId, agentId, now)
        if (updated == 0) {
            throw CrmConversationConflictException("La conversacion ya fue tomada por otro agente")
        }
        val claimed = requireConversation(conversationId)
        recordAssignment(
            conversationId = conversationId,
            type = CrmAssignmentEventType.CLAIM,
            fromUserId = null,
            toUserId = agentId,
            note = null
        )
        publishUpdated(claimed, operatorUsername)
        return claimed.toDto()
    }

    @Transactional
    fun release(
        conversationId: Long,
        agentId: Int,
        operatorUsername: String?,
        isAdmin: Boolean,
        note: String? = null
    ): CrmConversationDto {
        val conversation = requireConversation(conversationId)
        assertAssigneeOrAdmin(conversation, agentId, isAdmin)
        val previous = conversation.assignedAgentId
        conversation.assignedAgentId = null
        conversation.status = CrmConversationStatus.PENDING
        conversation.claimedAt = null
        conversation.updatedAt = LocalDateTime.now()
        val saved = conversationRepository.save(conversation)
        recordAssignment(
            conversationId = conversationId,
            type = CrmAssignmentEventType.RELEASE,
            fromUserId = previous,
            toUserId = null,
            note = note
        )
        publishUpdated(saved, operatorUsername)
        return saved.toDto()
    }

    @Transactional
    fun transfer(
        conversationId: Long,
        fromAgentId: Int,
        toAgentId: Int,
        note: String,
        operatorUsername: String?,
        isAdmin: Boolean
    ): CrmConversationDto {
        if (note.isBlank()) {
            throw CrmConversationValidationException("La transferencia requiere una nota")
        }
        if (toAgentId <= 0) {
            throw CrmConversationValidationException("toAgentId invalido")
        }
        val conversation = requireConversation(conversationId)
        assertAssigneeOrAdmin(conversation, fromAgentId, isAdmin)
        val previous = conversation.assignedAgentId
        conversation.assignedAgentId = toAgentId
        conversation.status = CrmConversationStatus.ASSIGNED
        conversation.claimedAt = LocalDateTime.now()
        conversation.updatedAt = LocalDateTime.now()
        val saved = conversationRepository.save(conversation)
        recordAssignment(
            conversationId = conversationId,
            type = CrmAssignmentEventType.TRANSFER,
            fromUserId = previous,
            toUserId = toAgentId,
            note = note.trim()
        )
        publishUpdated(saved, operatorUsername)
        return saved.toDto()
    }

    @Transactional
    fun resolve(
        conversationId: Long,
        agentId: Int,
        operatorUsername: String?,
        isAdmin: Boolean,
        resumeBot: Boolean,
        note: String?
    ): CrmConversationDto {
        val conversation = requireConversation(conversationId)
        assertAssigneeOrAdmin(conversation, agentId, isAdmin)
        val previous = conversation.assignedAgentId
        val now = LocalDateTime.now()
        conversation.status = CrmConversationStatus.RESOLVED
        conversation.assignedAgentId = null
        conversation.resolvedAt = now
        conversation.updatedAt = now
        val saved = conversationRepository.save(conversation)
        recordAssignment(
            conversationId = conversationId,
            type = CrmAssignmentEventType.RESOLVE,
            fromUserId = previous,
            toUserId = null,
            note = note
        )
        if (resumeBot) {
            handoffService.resumeBotAndTakeControl(saved.phone, "crm_resolved")
        }
        publishUpdated(saved, operatorUsername)
        return saved.toDto()
    }

    @Transactional
    fun reopen(
        conversationId: Long,
        agentId: Int,
        operatorUsername: String?,
        isAdmin: Boolean,
        note: String?
    ): CrmConversationDto {
        val conversation = requireConversation(conversationId)
        if (conversation.status != CrmConversationStatus.RESOLVED) {
            throw CrmConversationConflictException("Solo se pueden reabrir conversaciones resueltas")
        }
        if (!isAdmin && conversation.assignedAgentId != null && conversation.assignedAgentId != agentId) {
            throw CrmConversationForbiddenException("No puedes reabrir esta conversacion")
        }
        conversation.status = CrmConversationStatus.REOPENED
        conversation.assignedAgentId = null
        conversation.claimedAt = null
        conversation.resolvedAt = null
        conversation.updatedAt = LocalDateTime.now()
        val saved = conversationRepository.save(conversation)
        recordAssignment(
            conversationId = conversationId,
            type = CrmAssignmentEventType.REOPEN,
            fromUserId = agentId,
            toUserId = null,
            note = note
        )
        publishUpdated(saved, operatorUsername)
        return saved.toDto()
    }

    @Transactional
    fun markPendingOnHandoff(
        phone: String,
        subscriptionId: Int?,
        reason: String,
        handoffSummary: String? = null
    ): CrmConversation {
        val now = LocalDateTime.now()
        val existing = findWhatsAppConversation(phone)
        val conversation = if (existing == null) {
            CrmConversation(
                channel = CrmChannel.WHATSAPP,
                phone = PeruvianWhatsAppPhone.canonicalStoragePhone(phone),
                subscriptionId = subscriptionId,
                status = CrmConversationStatus.PENDING,
                lastInboundAt = now,
                handoffSummary = handoffSummary?.take(4000),
                createdAt = now,
                updatedAt = now
            )
        } else {
            if (existing.assignedAgentId == null) {
                existing.status = CrmConversationStatus.PENDING
                existing.claimedAt = null
            }
            existing.subscriptionId = subscriptionId ?: existing.subscriptionId
            existing.resolvedAt = null
            existing.updatedAt = now
            if (!handoffSummary.isNullOrBlank()) {
                existing.handoffSummary = handoffSummary.take(4000)
            }
            if (existing.lastInboundAt == null) {
                existing.lastInboundAt = now
            }
            existing
        }
        val saved = conversationRepository.save(conversation)
        publishUpdated(
            saved,
            null,
            extra = mapOf(
                "handoffReason" to reason,
                "handoffSummary" to saved.handoffSummary
            )
        )
        return saved
    }

    @Transactional
    fun pauseBot(conversationId: Long, agentId: Int, operatorUsername: String?, isAdmin: Boolean, note: String?): CrmConversationDto {
        val conversation = requireConversation(conversationId)
        assertAssigneeOrAdmin(conversation, agentId, isAdmin)
        handoffService.pauseBotAndPassToAdvisor(conversation.phone, note?.takeIf { it.isNotBlank() } ?: "agent_pause_bot")
        return requireConversation(conversationId).toDto()
    }

    @Transactional
    fun resumeBot(conversationId: Long, agentId: Int, operatorUsername: String?, isAdmin: Boolean, note: String?): CrmConversationDto {
        val conversation = requireConversation(conversationId)
        assertAssigneeOrAdmin(conversation, agentId, isAdmin)
        handoffService.resumeBotAndTakeControl(conversation.phone, note?.takeIf { it.isNotBlank() } ?: "agent_resume_bot")
        return requireConversation(conversationId).toDto()
    }

    fun suggestReply(conversationId: Long): CrmSuggestReplyDto {
        val conversation = requireConversation(conversationId)
        val recent = recentMessagesForPhone(conversation.phone)
        val context = buildString {
            append("status=").append(conversation.status.name)
            conversation.subscriptionId?.let { append(";subscriptionId=").append(it) }
            conversation.handoffSummary?.let { append(";handoff=").append(it.take(500)) }
        }
        val suggestion = llmClient.suggestReply(recent, context)
            ?: "No hay sugerencia disponible (LLM deshabilitado o sin respuesta). Redacta manualmente."
        return CrmSuggestReplyDto(
            suggestion = suggestion,
            source = if (suggestion.startsWith("No hay sugerencia")) "NONE" else "LLM",
            requiresConfirmation = true
        )
    }

    fun recentMessagesForPhone(phone: String, limit: Int = 12): List<String> {
        val page = PageRequest.of(0, limit)
        val inbound = inboundMessageRepository.findByPhoneOrderByCreatedAtDesc(phone, page)
            .map { "Cliente: ${(it.messageText ?: it.buttonReplyTitle ?: it.messageType).orEmpty()}" }
        val outbound = messageLogRepository.findTop10ByPhoneOrderByCreatedAtDesc(phone)
            .map { "Agente/Bot: ${it.message.orEmpty()}" }
        return (inbound + outbound).take(limit)
    }

    @Transactional
    fun touchInbound(phone: String, subscriptionId: Int?): CrmConversation {
        val now = LocalDateTime.now()
        val existing = findWhatsAppConversation(phone)
        val conversation = if (existing == null) {
            CrmConversation(
                channel = CrmChannel.WHATSAPP,
                phone = PeruvianWhatsAppPhone.canonicalStoragePhone(phone),
                subscriptionId = subscriptionId,
                status = CrmConversationStatus.NEW,
                lastInboundAt = now,
                createdAt = now,
                updatedAt = now
            )
        } else {
            if (existing.status == CrmConversationStatus.RESOLVED) {
                existing.status = CrmConversationStatus.REOPENED
                existing.assignedAgentId = null
                existing.claimedAt = null
                existing.resolvedAt = null
            }
            existing.subscriptionId = subscriptionId ?: existing.subscriptionId
            existing.lastInboundAt = now
            existing.updatedAt = now
            existing
        }
        return conversationRepository.save(conversation)
    }

    @Transactional
    fun touchOutbound(phone: String, createIfMissing: Boolean = false) {
        val now = LocalDateTime.now()
        val existing = findWhatsAppConversation(phone)
        if (existing == null) {
            if (!createIfMissing) return
            conversationRepository.save(
                CrmConversation(
                    channel = CrmChannel.WHATSAPP,
                    phone = PeruvianWhatsAppPhone.canonicalStoragePhone(phone),
                    status = CrmConversationStatus.NEW,
                    lastOutboundAt = now,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            return
        }
        existing.lastOutboundAt = now
        existing.updatedAt = now
        conversationRepository.save(existing)
    }

    fun assertCanReply(phone: String, agentId: Int?, isAdmin: Boolean) {
        val conversation = findWhatsAppConversation(phone)
            ?: throw CrmConversationForbiddenException("Debes tomar la conversacion antes de responder")
        if (conversation.status == CrmConversationStatus.RESOLVED) {
            throw CrmConversationForbiddenException("La conversacion esta resuelta")
        }
        if (isAdmin) return
        if (conversation.assignedAgentId == null) {
            throw CrmConversationForbiddenException("Debes tomar la conversacion antes de responder")
        }
        if (conversation.assignedAgentId != agentId) {
            throw CrmConversationForbiddenException("Solo el agente asignado o un ADMIN puede responder")
        }
    }

    fun listAssignmentHistory(conversationId: Long): List<CrmAssignmentEventDto> {
        requireConversation(conversationId)
        return assignmentEventRepository.findByConversationIdOrderByCreatedAtDesc(conversationId)
            .map { it.toDto() }
    }

    fun listInternalNotes(conversationId: Long): List<CrmInternalNoteDto> {
        requireConversation(conversationId)
        return internalNoteRepository.findByConversationIdOrderByCreatedAtDesc(conversationId)
            .map { it.toDto() }
    }

    @Transactional
    fun addInternalNote(
        conversationId: Long,
        authorId: Int,
        text: String,
        operatorUsername: String?
    ): CrmInternalNoteDto {
        requireConversation(conversationId)
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            throw CrmConversationValidationException("La nota no puede estar vacia")
        }
        val saved = internalNoteRepository.save(
            CrmInternalNote(
                conversationId = conversationId,
                authorId = authorId,
                text = trimmed.take(2000),
                createdAt = LocalDateTime.now()
            )
        )
        return saved.toDto()
    }

    private fun requireConversation(id: Long): CrmConversation =
        conversationRepository.findById(id).orElse(null)
            ?: throw CrmConversationNotFoundException("Conversacion CRM no encontrada: $id")

    private fun assertAssigneeOrAdmin(conversation: CrmConversation, agentId: Int, isAdmin: Boolean) {
        if (isAdmin) return
        if (conversation.assignedAgentId != agentId) {
            throw CrmConversationForbiddenException("Solo el agente asignado o un ADMIN puede ejecutar esta accion")
        }
    }

    private fun recordAssignment(
        conversationId: Long,
        type: CrmAssignmentEventType,
        fromUserId: Int?,
        toUserId: Int?,
        note: String?
    ) {
        assignmentEventRepository.save(
            CrmAssignmentEvent(
                conversationId = conversationId,
                eventType = type,
                fromUserId = fromUserId,
                toUserId = toUserId,
                note = note?.take(1000),
                createdAt = LocalDateTime.now()
            )
        )
    }

    private fun publishUpdated(
        conversation: CrmConversation,
        operatorUsername: String?,
        extra: Map<String, Any?> = emptyMap()
    ) {
        val payload = mutableMapOf<String, Any?>(
            "conversationId" to conversation.id,
            "phone" to conversation.phone,
            "channel" to conversation.channel.name,
            "status" to conversation.status.name,
            "assignedAgentId" to conversation.assignedAgentId,
            "assignedAgentName" to conversation.assignedAgentId?.let { agentName(it) },
            "priority" to conversation.priority,
            "subscriptionId" to conversation.subscriptionId,
            "claimedAt" to conversation.claimedAt?.toString(),
            "resolvedAt" to conversation.resolvedAt?.toString(),
            "lastInboundAt" to conversation.lastInboundAt?.toString(),
            "lastOutboundAt" to conversation.lastOutboundAt?.toString(),
            "operatorUsername" to operatorUsername
        )
        payload.putAll(extra)
        crmEventPublisher.publish(CrmEventPublisher.CONVERSATION_UPDATED, payload)
    }

    private fun CrmConversation.toDto(): CrmConversationDto =
        CrmConversationDto(
            id = requireNotNull(id),
            channel = channel.name,
            phone = phone,
            subscriptionId = subscriptionId,
            status = status.name,
            assignedAgentId = assignedAgentId,
            assignedAgentName = assignedAgentId?.let { agentName(it) },
            priority = priority,
            claimedAt = claimedAt,
            resolvedAt = resolvedAt,
            lastInboundAt = lastInboundAt,
            lastOutboundAt = lastOutboundAt,
            handoffSummary = handoffSummary,
            botPaused = chatStateService.isBotPaused(phone),
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )

    private fun CrmAssignmentEvent.toDto(): CrmAssignmentEventDto =
        CrmAssignmentEventDto(
            id = requireNotNull(id),
            conversationId = conversationId,
            eventType = eventType.name,
            fromUserId = fromUserId,
            fromUserName = fromUserId?.let { agentName(it) },
            toUserId = toUserId,
            toUserName = toUserId?.let { agentName(it) },
            note = note,
            createdAt = createdAt
        )

    private fun CrmInternalNote.toDto(): CrmInternalNoteDto =
        CrmInternalNoteDto(
            id = requireNotNull(id),
            conversationId = conversationId,
            authorId = authorId,
            authorName = agentName(authorId),
            text = text,
            createdAt = createdAt
        )

    private fun agentName(userId: Int): String? {
        val user = userRepository.findById(userId).orElse(null) ?: return null
        return listOfNotNull(user.name, user.lastName)
            .joinToString(" ")
            .trim()
            .ifBlank { user.username }
    }

    private fun findWhatsAppConversation(phone: String): CrmConversation? =
        PeruvianWhatsAppPhone.queryVariants(phone).firstNotNullOfOrNull { variant ->
            conversationRepository.findByPhoneAndChannel(variant, CrmChannel.WHATSAPP)
        }

    companion object {
        private val CLAIMABLE_STATUSES = setOf(
            CrmConversationStatus.NEW,
            CrmConversationStatus.PENDING,
            CrmConversationStatus.REOPENED
        )
    }
}
