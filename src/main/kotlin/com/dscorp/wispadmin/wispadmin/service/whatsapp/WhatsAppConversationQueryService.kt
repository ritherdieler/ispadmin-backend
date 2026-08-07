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
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppThreadPageDto
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
    private val templateDisplayService: WhatsAppTemplateDisplayService,
    private val operatorDisplayNameResolver: WhatsAppOperatorDisplayNameResolver,
) {

    fun listConversations(filter: WhatsAppConversationFilter): List<WhatsAppConversationSummaryDto> {
        val inbound = loadInbound(filter.dateFrom, filter.dateTo)
        val outbound = loadOutbound(filter.dateFrom, filter.dateTo)

        val phones = (
            inbound.map { PeruvianWhatsAppPhone.canonicalConversationKey(it.phone) } +
                outbound.mapNotNull { it.phone?.let { p -> PeruvianWhatsAppPhone.canonicalConversationKey(p) } }
            ).distinct()
        val inboundByPhone = inbound.groupBy { PeruvianWhatsAppPhone.canonicalConversationKey(it.phone) }
        val outboundByPhone = outbound
            .filter { it.phone != null }
            .groupBy { PeruvianWhatsAppPhone.canonicalConversationKey(it.phone!!) }

        val subscriptionIds = inbound.mapNotNull { it.subscriptionId }.distinct()
        val subscriptions = if (subscriptionIds.isEmpty()) {
            emptyMap()
        } else {
            subscriptionRepository.findAllById(subscriptionIds).associateBy { it.id }
        }
        val serviceWindows = serviceWindowService.getServiceWindows(
            phones.flatMap { PeruvianWhatsAppPhone.queryVariants(it) }.distinct()
        )

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
                val window = pickBestServiceWindow(
                    PeruvianWhatsAppPhone.queryVariants(phone).mapNotNull { serviceWindows[it] }
                )
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
                    lastInboundAt = lastInbound?.createdAt,
                    lastOutboundAt = lastOutbound?.createdAt,
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
            .sortedWith(
                compareByDescending<WhatsAppConversationSummaryDto> { it.lastInboundAt ?: LocalDateTime.MIN }
                    .thenByDescending { it.lastMessageAt }
            )
            .take(filter.limit.coerceIn(1, 500))
            .toList()
    }

    fun getThread(
        phone: String,
        dateFrom: LocalDateTime? = null,
        dateTo: LocalDateTime? = null,
        limit: Int = 50,
        before: LocalDateTime? = null
    ): WhatsAppThreadPageDto {
        val pageSize = limit.coerceIn(1, 200)
        val fetchSize = pageSize + 1
        val pageable = PageRequest.of(0, fetchSize)
        val upperBound = when {
            before != null && dateTo != null -> if (before.isBefore(dateTo)) before else dateTo
            before != null -> before
            else -> dateTo
        }
        val phones = PeruvianWhatsAppPhone.queryVariants(phone)
        val inboundRecent = fetchInboundRecent(phones, dateFrom, upperBound, before, pageable, fetchSize)
        val outboundRecent = fetchOutboundRecent(phones, dateFrom, upperBound, before, pageable, fetchSize)

        val mergedDesc = (
            inboundRecent
                .filter { WhatsAppThreadMessageMapper.isRenderableInbound(it) }
                .map { with(WhatsAppThreadMessageMapper) { it.toThreadMessage() } } +
                outboundRecent.map { with(WhatsAppThreadMessageMapper) { it.toThreadMessage(templateDisplayService) } }
            )
            .filter { WhatsAppThreadMessageMapper.isRenderableThreadMessage(it) }
            .sortedByDescending { it.createdAt }
            .distinctBy { it.id }
        val hasMore = mergedDesc.size > pageSize
        val pageDesc = mergedDesc.take(pageSize)
        val messages = pageDesc.sortedBy { it.createdAt }
        return WhatsAppThreadPageDto(
            messages = operatorDisplayNameResolver.enrichMessages(messages),
            hasMore = hasMore,
            nextBefore = messages.firstOrNull()?.createdAt
        )
    }

    fun getContext(phone: String): WhatsAppConversationContextDto {
        val phones = PeruvianWhatsAppPhone.queryVariants(phone)
        val latestInbound = phones
            .flatMap { inboundMessageRepository.findTop1ByPhoneOrderByCreatedAtDesc(it) }
            .maxByOrNull { it.createdAt }
        val subscription = latestInbound?.subscriptionId?.let { subscriptionRepository.findById(it).orElse(null) }
            ?: findSubscriptionByPhone(phone)

        val pending = subscription?.payments?.filter { !it.paid }.orEmpty()
        val window = pickBestServiceWindow(phones.map { serviceWindowService.getServiceWindow(it) })
            ?: serviceWindowService.getServiceWindow(phone)
        val recentLogs = phones
            .flatMap { messageLogRepository.findTop10ByPhoneOrderByCreatedAtDesc(it) }
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
            .take(10)
            .map { it.toDto() }
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
                phones.firstNotNullOfOrNull { variant ->
                    crmConversationRepository.findByPhoneAndChannel(
                        variant,
                        com.dscorp.wispadmin.wispadmin.data.model.CrmChannel.WHATSAPP
                    )
                }
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
            phone = PeruvianWhatsAppPhone.canonicalConversationKey(phone),
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

    private fun fetchInboundRecent(
        phones: List<String>,
        dateFrom: LocalDateTime?,
        upperBound: LocalDateTime?,
        before: LocalDateTime?,
        pageable: PageRequest,
        fetchSize: Int
    ): List<WhatsAppInboundMessage> {
        if (phones.size == 1) {
            return queryInboundRecent(phones.single(), dateFrom, upperBound, before, pageable)
        }
        return phones
            .flatMap { queryInboundRecent(it, dateFrom, upperBound, before, pageable) }
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
            .take(fetchSize)
    }

    private fun fetchOutboundRecent(
        phones: List<String>,
        dateFrom: LocalDateTime?,
        upperBound: LocalDateTime?,
        before: LocalDateTime?,
        pageable: PageRequest,
        fetchSize: Int
    ): List<WhatsAppMessageLog> {
        if (phones.size == 1) {
            return queryOutboundRecent(phones.single(), dateFrom, upperBound, before, pageable)
        }
        return phones
            .flatMap { queryOutboundRecent(it, dateFrom, upperBound, before, pageable) }
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
            .take(fetchSize)
    }

    private fun queryInboundRecent(
        phone: String,
        dateFrom: LocalDateTime?,
        upperBound: LocalDateTime?,
        before: LocalDateTime?,
        pageable: PageRequest
    ): List<WhatsAppInboundMessage> = when {
        dateFrom != null && upperBound != null ->
            inboundMessageRepository.findByPhoneAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                phone = phone,
                from = dateFrom,
                to = upperBound,
                pageable = pageable
            )
        before != null ->
            inboundMessageRepository.findByPhoneAndCreatedAtLessThanOrderByCreatedAtDesc(phone, before, pageable)
        else ->
            inboundMessageRepository.findByPhoneOrderByCreatedAtDesc(phone, pageable)
    }

    private fun queryOutboundRecent(
        phone: String,
        dateFrom: LocalDateTime?,
        upperBound: LocalDateTime?,
        before: LocalDateTime?,
        pageable: PageRequest
    ): List<WhatsAppMessageLog> = when {
        dateFrom != null && upperBound != null ->
            messageLogRepository.findByPhoneAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                phone = phone,
                from = dateFrom,
                to = upperBound,
                pageable = pageable
            )
        before != null ->
            messageLogRepository.findByPhoneAndCreatedAtLessThanOrderByCreatedAtDesc(phone, before, pageable)
        else ->
            messageLogRepository.findByPhoneOrderByCreatedAtDesc(phone, pageable)
    }

    private fun pickBestServiceWindow(
        windows: List<WhatsAppServiceWindowService.WhatsAppServiceWindowStatus>
    ): WhatsAppServiceWindowService.WhatsAppServiceWindowStatus? =
        windows.filter { it.open }.maxByOrNull { it.expiresAt ?: LocalDateTime.MIN }
            ?: windows.firstOrNull()

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
