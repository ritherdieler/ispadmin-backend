package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicket
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import com.dscorp.wispadmin.wispadmin.data.model.CrmChannel
import com.dscorp.wispadmin.wispadmin.data.model.Subscription
import com.dscorp.wispadmin.wispadmin.data.model.TicketConversationLink
import com.dscorp.wispadmin.wispadmin.dto.CrmCreateTicketResultDto
import com.dscorp.wispadmin.wispadmin.dto.CrmTicketDto
import com.dscorp.wispadmin.wispadmin.dto.CrmTicketStatusSummaryDto
import com.dscorp.wispadmin.wispadmin.repository.AssistanceTicketRepository
import com.dscorp.wispadmin.wispadmin.repository.CrmConversationRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.TicketConversationLinkRepository
import com.dscorp.wispadmin.wispadmin.service.TicketNotificationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

@Service
class CrmTicketLinkService(
    private val ticketRepository: AssistanceTicketRepository,
    private val linkRepository: TicketConversationLinkRepository,
    private val conversationRepository: CrmConversationRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val ticketNotificationService: TicketNotificationService,
    private val whatsAppProperties: WhatsAppProperties
) {

    @Transactional
    fun createTicketFromConversation(
        conversationId: Long,
        category: String,
        description: String,
        createdBy: String?,
        customerName: String? = null
    ): CrmCreateTicketResultDto {
        val conversation = conversationRepository.findById(conversationId)
            .orElseThrow { CrmConversationNotFoundException("Conversacion $conversationId no encontrada") }
        val normalizedCategory = category.trim().ifBlank { "Otros" }
        val normalizedDescription = description.trim().ifBlank { "Ticket desde WhatsApp" }
        val since = Date.from(
            Instant.now().minusSeconds(whatsAppProperties.autoReply.ticketDedupHours.coerceAtLeast(1) * 3600L)
        )
        val duplicate = ticketRepository.findOpenByPhoneAndCategorySince(
            phone = conversation.phone,
            category = normalizedCategory,
            since = since,
            openStatuses = OPEN_STATUSES
        ).firstOrNull()

        val ticket = if (duplicate != null) {
            duplicate
        } else {
            val subscription = conversation.subscriptionId
                ?.let { subscriptionRepository.findById(it).orElse(null) }
            createTicket(
                phone = conversation.phone,
                category = normalizedCategory,
                description = normalizedDescription,
                subscription = subscription,
                customerName = customerName
            )
        }

        ensureLink(ticket.id, conversation.id!!, createdBy)
        return CrmCreateTicketResultDto(
            ticket = toDto(ticket, conversation.id),
            deduplicated = duplicate != null
        )
    }

    @Transactional
    fun createGuidedFaultTicket(
        phone: String,
        conversationId: Long?,
        category: String,
        description: String,
        subscription: Subscription?,
        createdBy: String = "bot"
    ): CrmCreateTicketResultDto {
        val conversation = conversationId?.let {
            conversationRepository.findById(it).orElse(null)
        } ?: conversationRepository.findByPhoneAndChannel(normalizePhone(phone), CrmChannel.WHATSAPP)

        val resolvedConversationId = conversation?.id
        val resultPhone = conversation?.phone ?: normalizePhone(phone)
        val normalizedCategory = category.trim().ifBlank { "Sin Conexión a Internet" }
        val since = Date.from(
            Instant.now().minusSeconds(whatsAppProperties.autoReply.ticketDedupHours.coerceAtLeast(1) * 3600L)
        )
        val duplicate = ticketRepository.findOpenByPhoneAndCategorySince(
            phone = resultPhone,
            category = normalizedCategory,
            since = since,
            openStatuses = OPEN_STATUSES
        ).firstOrNull()

        val ticket = duplicate ?: createTicket(
            phone = resultPhone,
            category = normalizedCategory,
            description = description.trim().ifBlank { "Averia reportada por WhatsApp" },
            subscription = subscription ?: conversation?.subscriptionId?.let {
                subscriptionRepository.findById(it).orElse(null)
            },
            customerName = null
        )

        if (resolvedConversationId != null) {
            ensureLink(ticket.id, resolvedConversationId, createdBy)
        }
        return CrmCreateTicketResultDto(
            ticket = toDto(ticket, resolvedConversationId),
            deduplicated = duplicate != null
        )
    }

    fun listTicketsForConversation(conversationId: Long): List<CrmTicketDto> {
        val links = linkRepository.findByConversationIdOrderByCreatedAtDesc(conversationId)
        if (links.isEmpty()) return emptyList()
        val tickets = ticketRepository.findAllById(links.map { it.ticketId }).associateBy { it.id }
        return links.mapNotNull { link ->
            tickets[link.ticketId]?.let { toDto(it, link.conversationId) }
        }
    }

    fun listTicketsForPhone(phone: String): List<CrmTicketDto> {
        val normalized = normalizePhone(phone)
        return ticketRepository.findByPhoneOrderByCreatedAtDesc(normalized).map { ticket ->
            val link = linkRepository.findByTicketId(ticket.id)
            toDto(ticket, link?.conversationId)
        }
    }

    fun getConversationIdForTicket(ticketId: Int): Long? =
        linkRepository.findByTicketId(ticketId)?.conversationId

    fun buildStatusSummary(phone: String): List<CrmTicketStatusSummaryDto> {
        return listTicketsForPhone(phone)
            .filter {
                it.status in setOf(
                    AssistanceTicketStatus.PENDING,
                    AssistanceTicketStatus.ASSIGNED,
                    AssistanceTicketStatus.IN_PROGRESS,
                    AssistanceTicketStatus.REOPEN,
                    AssistanceTicketStatus.RESOLVED
                )
            }
            .take(5)
            .map {
                CrmTicketStatusSummaryDto(
                    ticketId = it.id,
                    status = it.status.name,
                    statusLabel = it.status.status,
                    category = it.category,
                    assignedTo = it.assignedTo,
                    createdAt = it.createdAt,
                    slaBreached = it.slaBreached
                )
            }
    }

    fun formatStatusReply(phone: String): String {
        val items = buildStatusSummary(phone)
        if (items.isEmpty()) {
            return "No encontramos tickets abiertos asociados a este numero. Si necesita reportar una averia, escriba *averia* o use el menu."
        }
        val lines = items.joinToString("\n") { item ->
            val sla = if (item.slaBreached) " ⚠️ SLA" else ""
            val assignee = item.assignedTo?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            "• Ticket #${item.ticketId}: ${item.statusLabel}$assignee · ${item.category}$sla"
        }
        return "📋 Estado de sus tickets:\n$lines"
    }

    private fun createTicket(
        phone: String,
        category: String,
        description: String,
        subscription: Subscription?,
        customerName: String?
    ): AssistanceTicket {
        val priority = priorityFor(category)
        val now = Date()
        val ticket = AssistanceTicket(
            phone = phone,
            category = category,
            description = description,
            status = AssistanceTicketStatus.PENDING,
            priority = priority,
            createdAt = now,
            scheduledAt = now,
            subscription = subscription,
            isExternalCustomer = subscription == null,
            externalCustomerName = if (subscription == null) {
                customerName?.trim()?.takeIf { it.isNotBlank() } ?: "Cliente WhatsApp"
            } else {
                null
            },
            placeName = subscription?.place?.name
        )
        val saved = ticketRepository.save(ticket)
        ticketNotificationService.notifyTicketCreated(saved.toLegacyDto())
        return saved
    }

    private fun ensureLink(ticketId: Int, conversationId: Long, createdBy: String?) {
        val existing = linkRepository.findByTicketId(ticketId)
        if (existing != null) {
            if (existing.conversationId != conversationId) {
                existing.conversationId = conversationId
                linkRepository.save(existing)
            }
            return
        }
        linkRepository.save(
            TicketConversationLink(
                ticketId = ticketId,
                conversationId = conversationId,
                createdBy = createdBy?.take(128),
                createdAt = LocalDateTime.now()
            )
        )
    }

    private fun toDto(ticket: AssistanceTicket, conversationId: Long?): CrmTicketDto {
        val createdAt = ticket.createdAt.toLocalDateTime()
        return CrmTicketDto(
            id = ticket.id,
            phone = ticket.phone,
            category = ticket.category,
            description = ticket.description,
            status = ticket.status,
            priority = priorityLabel(ticket.priority),
            createdAt = createdAt,
            assignedAt = ticket.assignedAt?.toLocalDateTime(),
            resolvedAt = ticket.resolvedAt?.toLocalDateTime(),
            closedAt = ticket.closedAt?.toLocalDateTime(),
            assignedTo = ticket.responsible?.let { "${it.name} ${it.lastName}".trim() },
            subscriptionId = ticket.subscription?.id,
            conversationId = conversationId,
            slaBreached = isSlaBreached(ticket),
            clientName = ticket.subscription?.getFullName()
                ?: ticket.externalCustomerName
        )
    }

    companion object {
        val OPEN_STATUSES = listOf(
            AssistanceTicketStatus.PENDING,
            AssistanceTicketStatus.ASSIGNED,
            AssistanceTicketStatus.IN_PROGRESS,
            AssistanceTicketStatus.REOPEN
        )

        fun isSlaBreached(ticket: AssistanceTicket, nowMillis: Long = System.currentTimeMillis()): Boolean {
            if (ticket.status in setOf(
                    AssistanceTicketStatus.CLOSED,
                    AssistanceTicketStatus.CANCELLED,
                    AssistanceTicketStatus.RESOLVED
                )
            ) {
                return false
            }
            val ageHours = (nowMillis - ticket.createdAt.time) / (60 * 60 * 1000.0)
            val limitHours = when {
                ticket.priority >= 10 -> 24.0
                ticket.priority >= 5 -> 48.0
                else -> 72.0
            }
            return ageHours >= limitHours
        }

        fun priorityFor(category: String): Int = when (category) {
            "Sin Conexión a Internet" -> 10
            "Cambio de Domicilio" -> 5
            "Otros" -> 3
            "Cambio de Contraseña" -> 2
            else -> 1
        }

        fun priorityLabel(priority: Int): String = when {
            priority > 5 -> "Alta"
            priority > 2 -> "Media"
            else -> "Baja"
        }

        fun normalizePhone(phone: String): String {
            val digits = phone.filter { it.isDigit() }
            return when {
                digits.length == 11 && digits.startsWith("51") -> digits.substring(2)
                digits.length == 9 && digits.startsWith("9") -> digits
                else -> digits
            }
        }

        private fun Date.toLocalDateTime(): LocalDateTime =
            LocalDateTime.ofInstant(toInstant(), ZoneId.systemDefault())
    }
}

private fun AssistanceTicket.toLegacyDto() = com.dscorp.wispadmin.wispadmin.dto.AssistanceTicketDto(
    id = id,
    name = subscription?.getFullName() ?: (externalCustomerName ?: "Cliente").uppercase(),
    phone = phone,
    category = category,
    description = description,
    status = status,
    comments = comments,
    priority = CrmTicketLinkService.priorityLabel(priority),
    createdAt = createdAt,
    scheduledAt = scheduledAt,
    assignedAt = assignedAt,
    resolvedAt = resolvedAt,
    closedAt = closedAt,
    assignedTo = responsible?.let { "${it.name} ${it.lastName}" }.orEmpty(),
    place = subscription?.place?.name ?: placeName,
    address = subscription?.address,
    sheetImageUrl = sheetImageUrl,
    isExternalCustomer = isExternalCustomer
)
