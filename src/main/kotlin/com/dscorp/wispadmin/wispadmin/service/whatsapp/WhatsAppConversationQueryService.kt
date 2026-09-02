package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.CrmChannel
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversation
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationContextDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationHistoryItemDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationInstallationOrderDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationPendingDebtDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationRecentPaymentDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationSubscriptionDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationPageDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationSummaryDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppConversationTicketItemDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppInboxViewCountsDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppThreadPageDto
import com.dscorp.wispadmin.wispadmin.dto.toDto
import com.dscorp.wispadmin.wispadmin.repository.CrmConversationRepository
import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime

data class WhatsAppConversationFilter(
    val search: String? = null,
    val dateFrom: LocalDateTime? = null,
    val dateTo: LocalDateTime? = null,
    val unreadOnly: Boolean = false,
    val limit: Int = 50,
    val view: WhatsAppInboxView = WhatsAppInboxView.ALL,
    val agentId: Int? = null,
    val cursor: LocalDateTime? = null
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

    fun listConversations(filter: WhatsAppConversationFilter): WhatsAppConversationPageDto {
        val pageSize = filter.limit.coerceIn(1, 100)
        val hasDateRange = filter.dateFrom != null && filter.dateTo != null
        if (hasDateRange) {
            val items = listConversationsWithDateRange(filter, pageSize)
            return WhatsAppConversationPageDto(items = items, hasMore = false, nextCursor = null)
        }

        if (
            filter.view == WhatsAppInboxView.ALL &&
            filter.search.isNullOrBlank() &&
            !filter.unreadOnly
        ) {
            return listAllViewPage(filter, pageSize)
        }

        return listFilteredViewPage(filter, pageSize)
    }

    private fun listAllViewPage(
        filter: WhatsAppConversationFilter,
        pageSize: Int
    ): WhatsAppConversationPageDto {
        val fetchLimit = pageSize + 1
        val rankedPhones = fetchRankedPhones(fetchLimit, filter.cursor)
        if (rankedPhones.isEmpty()) {
            return WhatsAppConversationPageDto(items = emptyList(), hasMore = false, nextCursor = null)
        }
        val canonicalPhones = rankedPhones
            .map { PeruvianWhatsAppPhone.canonicalConversationKey(it) }
            .distinct()
        val summaries = buildSummariesForPhones(canonicalPhones)
        val sorted = filterAndSortSummaries(summaries, filter)
        val page = sorted.take(pageSize)
        val hasMore = sorted.size > pageSize || rankedPhones.size > pageSize
        val nextCursor = if (hasMore) page.lastOrNull()?.lastMessageAt else null
        return WhatsAppConversationPageDto(items = page, hasMore = hasMore, nextCursor = nextCursor)
    }

    private fun listFilteredViewPage(
        filter: WhatsAppConversationFilter,
        pageSize: Int
    ): WhatsAppConversationPageDto {
        val accumulated = LinkedHashMap<String, WhatsAppConversationSummaryDto>()
        var rankCursor = filter.cursor
        var exhausted = false
        var rounds = 0
        while (accumulated.size <= pageSize && !exhausted && rounds < MAX_VIEW_SCAN_ROUNDS) {
            rounds++
            val rankedPhones = fetchRankedPhones(PHONE_SCAN_BATCH, rankCursor)
            if (rankedPhones.isEmpty()) {
                exhausted = true
                break
            }
            val canonicalPhones = rankedPhones
                .map { PeruvianWhatsAppPhone.canonicalConversationKey(it) }
                .distinct()
            val summaries = buildSummariesForPhones(canonicalPhones)
            filterAndSortSummaries(summaries, filter).forEach { summary ->
                accumulated.putIfAbsent(summary.phone, summary)
            }
            rankCursor = batchRankCursor(rankedPhones, summaries) ?: rankCursor
            if (rankedPhones.size < PHONE_SCAN_BATCH) {
                exhausted = true
            }
        }
        val sorted = accumulated.values.sortedWith(conversationSortComparator())
        val page = sorted.take(pageSize)
        val hasMore = sorted.size > pageSize || !exhausted
        val nextCursor = if (hasMore) page.lastOrNull()?.lastMessageAt else null
        return WhatsAppConversationPageDto(items = page, hasMore = hasMore, nextCursor = nextCursor)
    }

    private fun fetchRankedPhones(limit: Int, cursor: LocalDateTime?): List<String> =
        if (cursor == null) {
            inboundMessageRepository.findRecentActivePhones(limit)
        } else {
            inboundMessageRepository.findRecentActivePhonesBefore(limit, cursor)
        }

    private fun batchRankCursor(
        rankedPhones: List<String>,
        summaries: List<WhatsAppConversationSummaryDto>
    ): LocalDateTime? {
        val byPhone = summaries.associateBy { it.phone }
        return rankedPhones
            .map { PeruvianWhatsAppPhone.canonicalConversationKey(it) }
            .mapNotNull { byPhone[it]?.lastMessageAt }
            .minOrNull()
    }

    fun getInboxViewCounts(agentId: Int?): WhatsAppInboxViewCountsDto {
        val phones = inboundMessageRepository.findRecentActivePhones(RECENT_PHONE_RANK_LIMIT)
            .map { PeruvianWhatsAppPhone.canonicalConversationKey(it) }
            .distinct()
        val summaries = if (phones.isEmpty()) emptyList() else buildViewCountSignals(phones)
        fun count(view: WhatsAppInboxView) =
            summaries.count {
                WhatsAppInboxViewPolicy.matchesView(
                    view = view,
                    status = it.crmStatus,
                    assignedAgentId = it.assignedAgentId,
                    currentAgentId = agentId,
                    lastInboundAt = it.lastInboundAt,
                    lastOutboundAt = it.lastOutboundAt,
                    hasPendingReceipt = it.hasPendingReceipt,
                    hasPendingAdvisorRequest = it.hasPendingAdvisorRequest
                )
            }.toLong()

        return WhatsAppInboxViewCountsDto(
            queue = count(WhatsAppInboxView.QUEUE),
            mine = count(WhatsAppInboxView.MINE),
            team = count(WhatsAppInboxView.TEAM),
            receipts = count(WhatsAppInboxView.RECEIPTS),
            advisors = count(WhatsAppInboxView.ADVISORS),
            resolved = count(WhatsAppInboxView.RESOLVED),
            all = summaries.size.toLong(),
            totalUnread = inboundMessageRepository.countAllUnread(),
            conversationsWithUnread = inboundMessageRepository.countPhonesWithUnread()
        )
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
        val latestInbound = inboundMessageRepository.findTop1ByPhoneInOrderByCreatedAtDesc(phones)
            .maxByOrNull { it.createdAt }
        val subscription = latestInbound?.subscriptionId?.let { subscriptionRepository.findById(it).orElse(null) }
            ?: findSubscriptionByPhone(phone)

        val pending = subscription?.payments?.filter { !it.paid }.orEmpty()
        val serviceWindows = serviceWindowService.getServiceWindows(phones)
        val window = pickBestServiceWindow(phones.mapNotNull { serviceWindows[it] })
            ?: serviceWindowService.getServiceWindow(phone)
        val recentLogs = messageLogRepository
            .findByPhoneInOrderByCreatedAtDesc(phones, PageRequest.of(0, 10))
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
            paymentRepository.findTop5BySubscriptionIdOrderByBillingDateDatetimeDesc(subscriptionId)
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
                        CrmChannel.WHATSAPP
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

    private fun listConversationsWithDateRange(
        filter: WhatsAppConversationFilter,
        pageSize: Int
    ): List<WhatsAppConversationSummaryDto> {
        val inbound = inboundMessageRepository.findByCreatedAtBetween(filter.dateFrom!!, filter.dateTo!!)
        val outbound = messageLogRepository.findByCreatedAtBetween(filter.dateFrom, filter.dateTo!!)
        val phones = (
            inbound.map { PeruvianWhatsAppPhone.canonicalConversationKey(it.phone) } +
                outbound.mapNotNull { it.phone?.let { p -> PeruvianWhatsAppPhone.canonicalConversationKey(p) } }
            ).distinct()
        if (phones.isEmpty()) return emptyList()
        val summaries = buildSummariesFromLoadedMessages(phones, inbound, outbound)
        return applyListFilters(summaries, filter, pageSize)
    }

    private fun buildViewCountSignals(canonicalPhones: List<String>): List<InboxCountSignals> {
        val variants = canonicalPhones.flatMap { PeruvianWhatsAppPhone.queryVariants(it) }.distinct()
        if (variants.isEmpty()) return emptyList()

        val aggregated = LinkedHashMap<String, MutableList<LocalDateTime?>>()
        inboundMessageRepository.aggregateInboxSignalsByPhoneIn(variants).forEach { row ->
            val phone = PeruvianWhatsAppPhone.canonicalConversationKey(row[0].toString())
            val current = aggregated.getOrPut(phone) { mutableListOf(null, null, null, null) }
            (0 until SIGNAL_COLUMNS).forEach { index ->
                val value = toLocalDateTime(row[index + 1])
                if (value != null && current[index]?.isAfter(value) != true) {
                    current[index] = value
                }
            }
        }
        if (aggregated.isEmpty()) return emptyList()

        val crmByPhone = loadCrmByPhones(variants)
        return aggregated.mapNotNull { (phone, values) ->
            val lastInboundAt = values[0]
            val lastOutboundAt = values[1]
            if (lastInboundAt == null && lastOutboundAt == null) return@mapNotNull null
            val crm = pickBestCrm(phone, crmByPhone)
            val status = crm?.status?.name
            InboxCountSignals(
                lastInboundAt = lastInboundAt,
                lastOutboundAt = lastOutboundAt,
                crmStatus = status,
                assignedAgentId = crm?.assignedAgentId,
                hasPendingReceipt = WhatsAppInboxViewPolicy.hasPendingReceipt(
                    status = status,
                    resolvedAt = crm?.resolvedAt,
                    latestMediaAt = values[2]
                ),
                hasPendingAdvisorRequest = WhatsAppInboxViewPolicy.hasPendingAdvisorRequest(
                    status = status,
                    resolvedAt = crm?.resolvedAt,
                    latestAdvisorRequestAt = values[3]
                )
            )
        }
    }

    private fun buildSummariesForPhones(canonicalPhones: List<String>): List<WhatsAppConversationSummaryDto> {
        val variants = canonicalPhones.flatMap { PeruvianWhatsAppPhone.queryVariants(it) }.distinct()
        if (variants.isEmpty()) return emptyList()

        val latestInbound = inboundMessageRepository.findLatestInboundByPhoneIn(variants)
        val latestOutbound = messageLogRepository.findLatestOutboundByPhoneIn(variants)
        val unreadRows = inboundMessageRepository.countUnreadByPhoneIn(variants)
        val mediaRows = inboundMessageRepository.findLatestMediaAtByPhoneIn(variants)
        val advisorRows = inboundMessageRepository.findLatestAdvisorRequestAtByPhoneIn(variants)
        val subscriptionRows = inboundMessageRepository.findLatestSubscriptionIdByPhoneIn(variants)
        val buttonRows = inboundMessageRepository.findLatestButtonReplyIdByPhoneIn(variants)
        val crmByPhone = loadCrmByPhones(variants)

        val inboundByCanonical = latestInbound.groupBy { PeruvianWhatsAppPhone.canonicalConversationKey(it.phone) }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.createdAt } }
        val outboundByCanonical = latestOutbound
            .filter { it.phone != null }
            .groupBy { PeruvianWhatsAppPhone.canonicalConversationKey(it.phone!!) }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.createdAt } }
        val unreadByCanonical = unreadRows.associate { row ->
            PeruvianWhatsAppPhone.canonicalConversationKey(row[0].toString()) to (row[1] as Number).toInt()
        }
        val mediaAtByCanonical = mediaRows.associate { row ->
            PeruvianWhatsAppPhone.canonicalConversationKey(row[0].toString()) to toLocalDateTime(row[1])
        }
        val advisorAtByCanonical = advisorRows.associate { row ->
            PeruvianWhatsAppPhone.canonicalConversationKey(row[0].toString()) to toLocalDateTime(row[1])
        }
        val subscriptionByCanonical = subscriptionRows.associate { row ->
            PeruvianWhatsAppPhone.canonicalConversationKey(row[0].toString()) to (row[1] as Number).toInt()
        }
        val buttonByCanonical = buttonRows.associate { row ->
            PeruvianWhatsAppPhone.canonicalConversationKey(row[0].toString()) to row[1]?.toString()
        }

        val subscriptionIds = subscriptionByCanonical.values.distinct()
        val namesById = if (subscriptionIds.isEmpty()) {
            emptyMap()
        } else {
            subscriptionRepository.findNameProjectionsByIdIn(subscriptionIds).associate { projection ->
                projection.getId() to listOfNotNull(projection.getFirstName(), projection.getLastName())
                    .joinToString(" ")
                    .trim()
                    .takeIf { it.isNotBlank() && !it.contains("null") }
            }
        }

        val serviceWindows = serviceWindowService.getServiceWindows(variants)
        val previewInputs = canonicalPhones.mapNotNull { phone ->
            val lastInbound = inboundByCanonical[phone]
            val lastOutbound = outboundByCanonical[phone]
            if (lastOutbound != null && (lastInbound == null || !lastOutbound.createdAt.isBefore(lastInbound.createdAt))) {
                phone to (lastOutbound.message to lastOutbound.messageType)
            } else {
                null
            }
        }
        val renderedPreviews = templateDisplayService.displayStoredMessages(previewInputs.map { it.second })
        val previewByPhone = previewInputs.mapIndexed { index, (phone, _) -> phone to renderedPreviews[index] }.toMap()

        return canonicalPhones.mapNotNull { phone ->
            val lastInbound = inboundByCanonical[phone]
            val lastOutbound = outboundByCanonical[phone]
            if (lastInbound == null && lastOutbound == null) return@mapNotNull null
            val lastAt = listOfNotNull(lastInbound?.createdAt, lastOutbound?.createdAt).maxOrNull()
                ?: return@mapNotNull null
            val lastInboundAt = lastInbound?.createdAt
            val lastOutboundAt = lastOutbound?.createdAt
            val lastPreview = when {
                lastOutbound != null && (lastInbound == null || !lastOutbound.createdAt.isBefore(lastInbound.createdAt)) ->
                    previewByPhone[phone]
                else -> lastInbound?.messageText ?: lastInbound?.buttonReplyTitle
            }
            val subscriptionId = subscriptionByCanonical[phone] ?: lastInbound?.subscriptionId
            val crm = pickBestCrm(phone, crmByPhone)
            val status = crm?.status?.name
            val latestMediaAt = mediaAtByCanonical[phone]
            val pendingReceipt = WhatsAppInboxViewPolicy.hasPendingReceipt(
                status = status,
                resolvedAt = crm?.resolvedAt,
                latestMediaAt = latestMediaAt
            )
            val latestAdvisorAt = advisorAtByCanonical[phone]
            val pendingAdvisor = WhatsAppInboxViewPolicy.hasPendingAdvisorRequest(
                status = status,
                resolvedAt = crm?.resolvedAt,
                latestAdvisorRequestAt = latestAdvisorAt
            )
            val window = pickBestServiceWindow(
                PeruvianWhatsAppPhone.queryVariants(phone).mapNotNull { serviceWindows[it] }
            ) ?: WhatsAppServiceWindowService.WhatsAppServiceWindowStatus(
                phone = phone,
                open = false,
                expiresAt = null
            )

            WhatsAppConversationSummaryDto(
                phone = phone,
                clientName = subscriptionId?.let { namesById[it] },
                subscriptionId = subscriptionId,
                lastMessagePreview = lastPreview,
                lastMessageAt = lastAt,
                lastInboundAt = lastInboundAt,
                lastOutboundAt = lastOutboundAt,
                unreadCount = unreadByCanonical[phone] ?: 0,
                identified = subscriptionId != null,
                serviceWindowActive = window.open,
                serviceWindowExpiresAt = window.expiresAt,
                lastButtonReplyId = buttonByCanonical[phone],
                lastHasMedia = latestMediaAt != null,
                hasPendingReceipt = pendingReceipt,
                hasPendingAdvisorRequest = pendingAdvisor,
                crmConversationId = crm?.id,
                crmStatus = status,
                assignedAgentId = crm?.assignedAgentId,
                resolvedByAgentId = crm?.resolvedByAgentId,
                crmResolvedAt = crm?.resolvedAt
            )
        }
    }

    private fun buildSummariesFromLoadedMessages(
        canonicalPhones: List<String>,
        inbound: List<WhatsAppInboundMessage>,
        outbound: List<WhatsAppMessageLog>
    ): List<WhatsAppConversationSummaryDto> {
        val inboundByPhone = inbound.groupBy { PeruvianWhatsAppPhone.canonicalConversationKey(it.phone) }
        val outboundByPhone = outbound
            .filter { it.phone != null }
            .groupBy { PeruvianWhatsAppPhone.canonicalConversationKey(it.phone!!) }
        val variants = canonicalPhones.flatMap { PeruvianWhatsAppPhone.queryVariants(it) }.distinct()
        val crmByPhone = loadCrmByPhones(variants)
        val subscriptionIds = inbound.mapNotNull { it.subscriptionId }.distinct()
        val namesById = if (subscriptionIds.isEmpty()) {
            emptyMap()
        } else {
            subscriptionRepository.findNameProjectionsByIdIn(subscriptionIds).associate { projection ->
                projection.getId() to listOfNotNull(projection.getFirstName(), projection.getLastName())
                    .joinToString(" ")
                    .trim()
                    .takeIf { it.isNotBlank() && !it.contains("null") }
            }
        }
        val serviceWindows = serviceWindowService.getServiceWindows(variants)
        val previewInputs = canonicalPhones.mapNotNull { phone ->
            val lastInbound = inboundByPhone[phone].orEmpty().maxByOrNull { it.createdAt }
            val lastOutbound = outboundByPhone[phone].orEmpty().maxByOrNull { it.createdAt }
            if (lastOutbound != null && (lastInbound == null || !lastOutbound.createdAt.isBefore(lastInbound.createdAt))) {
                phone to (lastOutbound.message to lastOutbound.messageType)
            } else null
        }
        val renderedPreviews = templateDisplayService.displayStoredMessages(previewInputs.map { it.second })
        val previewByPhone = previewInputs.mapIndexed { index, (phone, _) -> phone to renderedPreviews[index] }.toMap()

        return canonicalPhones.mapNotNull { phone ->
            val phoneInbound = inboundByPhone[phone].orEmpty()
            val phoneOutbound = outboundByPhone[phone].orEmpty()
            if (phoneInbound.isEmpty() && phoneOutbound.isEmpty()) return@mapNotNull null
            val lastInbound = phoneInbound.maxByOrNull { it.createdAt }
            val lastOutbound = phoneOutbound.maxByOrNull { it.createdAt }
            val lastAt = listOfNotNull(lastInbound?.createdAt, lastOutbound?.createdAt).maxOrNull()
                ?: return@mapNotNull null
            val lastPreview = when {
                lastOutbound != null && (lastInbound == null || !lastOutbound.createdAt.isBefore(lastInbound.createdAt)) ->
                    previewByPhone[phone]
                else -> lastInbound?.messageText ?: lastInbound?.buttonReplyTitle
            }
            val subscriptionId = phoneInbound.mapNotNull { it.subscriptionId }.lastOrNull()
                ?: lastInbound?.subscriptionId
            val crm = pickBestCrm(phone, crmByPhone)
            val latestMediaAt = phoneInbound
                .filter { inboundIsPaymentProof(it) }
                .maxByOrNull { it.createdAt }
                ?.createdAt
            val pendingReceipt = WhatsAppInboxViewPolicy.hasPendingReceipt(
                status = crm?.status?.name,
                resolvedAt = crm?.resolvedAt,
                latestMediaAt = latestMediaAt
            )
            val latestAdvisorAt = phoneInbound
                .filter { it.buttonReplyId == WhatsAppBotMenuCatalog.ADVISOR }
                .maxByOrNull { it.createdAt }
                ?.createdAt
            val pendingAdvisor = WhatsAppInboxViewPolicy.hasPendingAdvisorRequest(
                status = crm?.status?.name,
                resolvedAt = crm?.resolvedAt,
                latestAdvisorRequestAt = latestAdvisorAt
            )
            val window = pickBestServiceWindow(
                PeruvianWhatsAppPhone.queryVariants(phone).mapNotNull { serviceWindows[it] }
            ) ?: WhatsAppServiceWindowService.WhatsAppServiceWindowStatus(
                phone = phone,
                open = false,
                expiresAt = null
            )
            WhatsAppConversationSummaryDto(
                phone = phone,
                clientName = subscriptionId?.let { namesById[it] },
                subscriptionId = subscriptionId,
                lastMessagePreview = lastPreview,
                lastMessageAt = lastAt,
                lastInboundAt = lastInbound?.createdAt,
                lastOutboundAt = lastOutbound?.createdAt,
                unreadCount = phoneInbound.count { it.readAt == null },
                identified = subscriptionId != null,
                serviceWindowActive = window.open,
                serviceWindowExpiresAt = window.expiresAt,
                lastButtonReplyId = phoneInbound
                    .asSequence()
                    .filter { !it.buttonReplyId.isNullOrBlank() }
                    .maxByOrNull { it.createdAt }
                    ?.buttonReplyId,
                lastHasMedia = latestMediaAt != null,
                hasPendingReceipt = pendingReceipt,
                hasPendingAdvisorRequest = pendingAdvisor,
                crmConversationId = crm?.id,
                crmStatus = crm?.status?.name,
                assignedAgentId = crm?.assignedAgentId,
                resolvedByAgentId = crm?.resolvedByAgentId,
                crmResolvedAt = crm?.resolvedAt
            )
        }
    }

    private fun conversationSortComparator(): Comparator<WhatsAppConversationSummaryDto> =
        compareByDescending<WhatsAppConversationSummaryDto> { it.lastInboundAt ?: LocalDateTime.MIN }
            .thenByDescending { it.lastMessageAt }

    private fun filterAndSortSummaries(
        summaries: List<WhatsAppConversationSummaryDto>,
        filter: WhatsAppConversationFilter
    ): List<WhatsAppConversationSummaryDto> =
        summaries.asSequence()
            .filter {
                WhatsAppInboxViewPolicy.matchesView(
                    view = filter.view,
                    status = it.crmStatus,
                    assignedAgentId = it.assignedAgentId,
                    currentAgentId = filter.agentId,
                    lastInboundAt = it.lastInboundAt,
                    lastOutboundAt = it.lastOutboundAt,
                    hasPendingReceipt = it.hasPendingReceipt,
                    hasPendingAdvisorRequest = it.hasPendingAdvisorRequest
                )
            }
            .filter { summary ->
                filter.search.isNullOrBlank() ||
                    summary.phone.contains(filter.search!!) ||
                    summary.clientName?.contains(filter.search, ignoreCase = true) == true ||
                    summary.lastMessagePreview?.contains(filter.search, ignoreCase = true) == true
            }
            .filter { !filter.unreadOnly || it.unreadCount > 0 }
            .sortedWith(conversationSortComparator())
            .toList()

    private fun applyListFilters(
        summaries: List<WhatsAppConversationSummaryDto>,
        filter: WhatsAppConversationFilter,
        pageSize: Int
    ): List<WhatsAppConversationSummaryDto> =
        filterAndSortSummaries(summaries, filter).take(pageSize)

    private fun loadCrmByPhones(variants: Collection<String>): Map<String, CrmConversation> {
        if (variants.isEmpty()) return emptyMap()
        return crmConversationRepository.findByChannelAndPhoneIn(CrmChannel.WHATSAPP, variants)
            .associateBy { PeruvianWhatsAppPhone.canonicalConversationKey(it.phone) }
    }

    private fun pickBestCrm(phone: String, crmByPhone: Map<String, CrmConversation>): CrmConversation? {
        PeruvianWhatsAppPhone.queryVariants(phone).forEach { variant ->
            crmByPhone[PeruvianWhatsAppPhone.canonicalConversationKey(variant)]?.let { return it }
        }
        return crmByPhone[phone]
    }

    private fun toLocalDateTime(value: Any?): LocalDateTime? = when (value) {
        null -> null
        is LocalDateTime -> value
        is Timestamp -> value.toLocalDateTime()
        is java.util.Date -> Timestamp(value.time).toLocalDateTime()
        else -> null
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

    private data class InboxCountSignals(
        val lastInboundAt: LocalDateTime?,
        val lastOutboundAt: LocalDateTime?,
        val crmStatus: String?,
        val assignedAgentId: Int?,
        val hasPendingReceipt: Boolean,
        val hasPendingAdvisorRequest: Boolean
    )

    companion object {
        private const val RECENT_PHONE_RANK_LIMIT = 500
        private const val PHONE_SCAN_BATCH = 100
        private const val MAX_VIEW_SCAN_ROUNDS = 20
        private const val SIGNAL_COLUMNS = 4

        fun inboundHasMedia(inbound: WhatsAppInboundMessage): Boolean =
            WhatsAppThreadMessageMapper.inboundHasMedia(inbound)

        fun inboundIsPaymentProof(inbound: WhatsAppInboundMessage): Boolean =
            WhatsAppThreadMessageMapper.inboundIsPaymentProof(inbound)
    }
}
