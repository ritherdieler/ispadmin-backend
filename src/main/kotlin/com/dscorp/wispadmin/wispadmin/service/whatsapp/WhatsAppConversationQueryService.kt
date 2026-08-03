package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationContextDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationHistoryItemDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationInstallationOrderDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationPendingDebtDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationRecentPaymentDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationSubscriptionDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationSummaryDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationTicketItemDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppThreadMessageDto
import com.dscorp.wispadmin.wispadmin.dto.toDto
import com.dscorp.wispadmin.wispadmin.repository.CrmConversationRepository
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDateTime

data class WhatsAppConversationFilter(
    val search: String? = null,
    val dateFrom: LocalDateTime? = null,
    val dateTo: LocalDateTime? = null,
    val unreadOnly: Boolean = false,
    val limit: Int = 50
)

@Service
class WhatsAppConversationQueryService(
    private val inboundMessageRepository: WhatsAppInboundMessageRepository,
    private val messageLogRepository: WhatsAppMessageLogRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val serviceWindowService: WhatsAppServiceWindowService,
    private val paymentRepository: PaymentRepository,
    private val crmConversationRepository: CrmConversationRepository,
    private val crmTicketLinkService: CrmTicketLinkService,
    private val templateDisplayService: WhatsAppTemplateDisplayService
) {

    fun listConversations(filter: WhatsAppConversationFilter): List<WhatsAppConversationSummaryDto> {
        val inbound = loadInbound(filter.dateFrom, filter.dateTo)
        val outbound = loadOutbound(filter.dateFrom, filter.dateTo)

        val phones = (inbound.map { it.phone } + outbound.mapNotNull { it.phone }).distinct()
        val inboundByPhone = inbound.groupBy { it.phone }
        val outboundByPhone = outbound.filter { it.phone != null }.groupBy { it.phone!! }

        val subscriptionIds = inbound.mapNotNull { it.subscriptionId }.distinct()
        val subscriptions = if (subscriptionIds.isEmpty()) {
            emptyMap()
        } else {
            subscriptionRepository.findAllById(subscriptionIds).associateBy { it.id }
        }
        val serviceWindows = serviceWindowService.getServiceWindows(phones)

        return phones.asSequence()
            .mapNotNull { phone ->
                val phoneInbound = inboundByPhone[phone].orEmpty()
                val phoneOutbound = outboundByPhone[phone].orEmpty()
                if (phoneInbound.isEmpty() && phoneOutbound.isEmpty()) return@mapNotNull null

                val lastInbound = phoneInbound.maxByOrNull { it.createdAt }
                val lastOutbound = phoneOutbound.maxByOrNull { it.createdAt }
                val lastAt = listOfNotNull(lastInbound?.createdAt, lastOutbound?.createdAt).maxOrNull()
                    ?: return@mapNotNull null

                val lastPreview = when {
                    lastOutbound != null && (lastInbound == null || !lastOutbound.createdAt.isBefore(lastInbound.createdAt)) ->
                        templateDisplayService.displayStoredMessage(lastOutbound.message, lastOutbound.messageType)
                    else -> lastInbound?.messageText ?: lastInbound?.buttonReplyTitle
                }

                val subscriptionId = phoneInbound.mapNotNull { it.subscriptionId }.lastOrNull()
                    ?: lastInbound?.subscriptionId
                val subscription = subscriptionId?.let { subscriptions[it] }
                val window = serviceWindows[phone]
                    ?: WhatsAppServiceWindowService.WhatsAppServiceWindowStatus(
                        phone = phone,
                        open = false,
                        expiresAt = null
                    )
                val unreadCount = phoneInbound.count { it.readAt == null }
                val lastButtonReplyId = phoneInbound
                    .asSequence()
                    .filter { !it.buttonReplyId.isNullOrBlank() }
                    .maxByOrNull { it.createdAt }
                    ?.buttonReplyId
                val lastHasMedia = phoneInbound.any { inboundHasMedia(it) }

                WhatsAppConversationSummaryDto(
                    phone = phone,
                    clientName = subscription?.getFullName()?.trim()?.takeIf { it.isNotBlank() && !it.contains("null") },
                    subscriptionId = subscriptionId,
                    lastMessagePreview = lastPreview,
                    lastMessageAt = lastAt,
                    unreadCount = unreadCount,
                    identified = subscriptionId != null,
                    serviceWindowActive = window.open,
                    serviceWindowExpiresAt = window.expiresAt,
                    lastButtonReplyId = lastButtonReplyId,
                    lastHasMedia = lastHasMedia
                )
            }
            .filter { summary ->
                filter.search.isNullOrBlank() ||
                    summary.phone.contains(filter.search!!) ||
                    summary.clientName?.contains(filter.search, ignoreCase = true) == true ||
                    summary.lastMessagePreview?.contains(filter.search, ignoreCase = true) == true
            }
            .filter { !filter.unreadOnly || it.unreadCount > 0 }
            .sortedByDescending { it.lastMessageAt }
            .take(filter.limit.coerceIn(1, 500))
            .toList()
    }

    fun getThread(
        phone: String,
        dateFrom: LocalDateTime? = null,
        dateTo: LocalDateTime? = null,
        limit: Int = 200
    ): List<WhatsAppThreadMessageDto> {
        val pageSize = limit.coerceIn(1, 1000)
        val pageable = PageRequest.of(0, pageSize)
        val inboundRecent = if (dateFrom != null && dateTo != null) {
            inboundMessageRepository.findByPhoneAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                phone = phone,
                from = dateFrom,
                to = dateTo,
                pageable = pageable
            )
        } else {
            inboundMessageRepository.findByPhoneOrderByCreatedAtDesc(phone, pageable)
        }
        val outboundRecent = if (dateFrom != null && dateTo != null) {
            messageLogRepository.findByPhoneAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                phone = phone,
                from = dateFrom,
                to = dateTo,
                pageable = pageable
            )
        } else {
            messageLogRepository.findByPhoneOrderByCreatedAtDesc(phone, pageable)
        }

        return (
            inboundRecent.map { with(WhatsAppThreadMessageMapper) { it.toThreadMessage() } } +
                outboundRecent.map { with(WhatsAppThreadMessageMapper) { it.toThreadMessage(templateDisplayService) } }
            )
            .sortedByDescending { it.createdAt }
            .take(pageSize)
            .sortedBy { it.createdAt }
    }

    fun getContext(phone: String): WhatsAppConversationContextDto {
        val latestInbound = inboundMessageRepository.findTop1ByPhoneOrderByCreatedAtDesc(phone).firstOrNull()
        val subscription = latestInbound?.subscriptionId?.let { subscriptionRepository.findById(it).orElse(null) }
            ?: findSubscriptionByPhone(phone)

        val pending = subscription?.payments?.filter { !it.paid }.orEmpty()
        val window = serviceWindowService.getServiceWindow(phone)
        val recentLogs = messageLogRepository.findTop10ByPhoneOrderByCreatedAtDesc(phone).map { it.toDto() }
        val subscriptionDto = subscription?.id?.let { id ->
            WhatsAppConversationSubscriptionDto(
                id = id,
                status = subscription.serviceStatus?.name,
                planName = subscription.plan?.name
            )
        }
        val pendingDebt = if (subscription == null) {
            null
        } else {
            WhatsAppConversationPendingDebtDto(
                amount = if (pending.isEmpty()) 0.0 else pending.sumOf { it.amountToPay },
                invoiceCount = pending.size
            )
        }

        val recentPayments = subscription?.id?.let { subscriptionId ->
            paymentRepository.findBySubscriptionIdOrderByBillingDateDatetimeDesc(subscriptionId)
                .take(5)
                .mapNotNull { payment ->
                    val paymentId = payment.id ?: return@mapNotNull null
                    WhatsAppConversationRecentPaymentDto(
                        id = paymentId,
                        amount = if (payment.paid) (payment.amountPaid ?: payment.amountToPay) else payment.amountToPay,
                        paid = payment.paid,
                        billingDate = payment.billingDateDatetime,
                        paymentDate = payment.paymentDateDatetime
                    )
                }
        }.orEmpty()

        val installationOrders = listOfNotNull(subscription?.installationOrder).map { order ->
            WhatsAppConversationInstallationOrderDto(
                id = order.id,
                status = order.status.name,
                scheduledDate = order.scheduledDate,
                createdAt = order.createdAt
            )
        }

        val conversationHistory = subscription?.id?.let { subscriptionId ->
            crmConversationRepository.findBySubscriptionIdOrderByLastInboundAtDesc(subscriptionId)
                .take(5)
                .map {
                    WhatsAppConversationHistoryItemDto(
                        conversationId = it.id ?: 0L,
                        phone = it.phone,
                        status = it.status.name,
                        lastInboundAt = it.lastInboundAt,
                        lastOutboundAt = it.lastOutboundAt
                    )
                }
        }.orEmpty().ifEmpty {
            listOfNotNull(
                crmConversationRepository.findByPhoneAndChannel(
                    normalizePhone(phone),
                    com.dscorp.wispadmin.wispadmin.data.model.CrmChannel.WHATSAPP
                )
            ).map {
                WhatsAppConversationHistoryItemDto(
                    conversationId = it.id ?: 0L,
                    phone = it.phone,
                    status = it.status.name,
                    lastInboundAt = it.lastInboundAt,
                    lastOutboundAt = it.lastOutboundAt
                )
            }
        }

        val tickets = crmTicketLinkService.listTicketsForPhone(phone).take(10).map {
            WhatsAppConversationTicketItemDto(
                id = it.id,
                category = it.category,
                status = it.status.name,
                statusLabel = it.status.status,
                priority = it.priority,
                createdAt = it.createdAt,
                assignedTo = it.assignedTo,
                conversationId = it.conversationId,
                slaBreached = it.slaBreached
            )
        }

        return WhatsAppConversationContextDto(
            phone = phone,
            clientName = subscription?.getFullName()?.trim()?.takeIf { it.isNotBlank() && !it.contains("null") },
            identified = subscription?.id != null,
            subscription = subscriptionDto,
            pendingDebt = pendingDebt,
            recentLogs = recentLogs,
            serviceWindowActive = window.open,
            serviceWindowExpiresAt = window.expiresAt,
            recentPayments = recentPayments,
            installationOrders = installationOrders,
            conversationHistory = conversationHistory,
            tickets = tickets
        )
    }

    private fun loadInbound(dateFrom: LocalDateTime?, dateTo: LocalDateTime?): List<WhatsAppInboundMessage> {
        return if (dateFrom != null && dateTo != null) {
            inboundMessageRepository.findByCreatedAtBetween(dateFrom, dateTo)
        } else {
            inboundMessageRepository.findTop500ByOrderByCreatedAtDesc()
        }
    }

    private fun loadOutbound(dateFrom: LocalDateTime?, dateTo: LocalDateTime?): List<WhatsAppMessageLog> {
        return if (dateFrom != null && dateTo != null) {
            messageLogRepository.findByCreatedAtBetween(dateFrom, dateTo)
        } else {
            messageLogRepository.findTop500ByOrderByCreatedAtDesc()
        }
    }

    private fun findSubscriptionByPhone(phone: String) =
        subscriptionRepository.findByNormalizedPhone(normalizePhone(phone)).firstOrNull()

    private fun normalizePhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.length == 11 && digits.startsWith("51") -> digits.substring(2)
            digits.length == 9 && digits.startsWith("9") -> digits
            else -> digits
        }
    }

    companion object {
        fun inboundHasMedia(inbound: WhatsAppInboundMessage): Boolean =
            WhatsAppThreadMessageMapper.inboundHasMedia(inbound)
    }
}
