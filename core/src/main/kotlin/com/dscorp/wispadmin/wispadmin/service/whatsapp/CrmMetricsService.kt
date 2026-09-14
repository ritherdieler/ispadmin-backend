package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.CrmMetricsProperties
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicket
import com.dscorp.wispadmin.wispadmin.data.model.CrmAssignmentEventType
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversation
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversationStatus
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.dto.CrmMetricsAgentLoadDto
import com.dscorp.wispadmin.wispadmin.dto.CrmMetricsCsatReferenceDto
import com.dscorp.wispadmin.wispadmin.dto.CrmMetricsExcessiveWaitDto
import com.dscorp.wispadmin.wispadmin.dto.CrmMetricsIntentCountDto
import com.dscorp.wispadmin.wispadmin.dto.CrmMetricsSampleBucketDto
import com.dscorp.wispadmin.wispadmin.dto.CrmMetricsShiftHandoffDto
import com.dscorp.wispadmin.wispadmin.dto.CrmMetricsSummaryDto
import com.dscorp.wispadmin.wispadmin.repository.AssistanceTicketRepository
import com.dscorp.wispadmin.wispadmin.repository.CrmAssignmentEventRepository
import com.dscorp.wispadmin.wispadmin.repository.CrmConversationRepository
import com.dscorp.wispadmin.wispadmin.repository.TicketConversationLinkRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppAuditLogRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.round

@Service
class CrmMetricsService(
    private val conversationRepository: CrmConversationRepository,
    private val assignmentEventRepository: CrmAssignmentEventRepository,
    private val messageLogRepository: WhatsAppMessageLogRepository,
    private val auditLogRepository: WhatsAppAuditLogRepository,
    private val ticketLinkRepository: TicketConversationLinkRepository,
    private val ticketRepository: AssistanceTicketRepository,
    private val csatSurveyService: CsatSurveyService,
    private val properties: CrmMetricsProperties
) {

    fun buildSummary(from: LocalDateTime, to: LocalDateTime): CrmMetricsSummaryDto {
        val resolved = conversationRepository.findByResolvedAtBetween(from, to)
        val assignmentEvents = assignmentEventRepository.findByCreatedAtBetween(from, to)
        val operatorMessages = messageLogRepository.findByMessageTypeInAndCreatedAtBetween(
            OPERATOR_MESSAGE_TYPES,
            from,
            to
        )
        val botTraces = auditLogRepository.findByActionAndCreatedAtBetween(
            WhatsAppAuditService.ACTION_BOT_TRACE,
            from,
            to
        )
        val ticketLinks = ticketLinkRepository.findByCreatedAtBetween(from, to)
        val openQueue = conversationRepository.findByStatusIn(QUEUE_STATUSES)
        val assignedActive = conversationRepository.findByStatusIn(listOf(CrmConversationStatus.ASSIGNED))

        val repliesByPhone = operatorMessages
            .filter { !it.phone.isNullOrBlank() && !it.operatorUsername.isNullOrBlank() }
            .groupBy { normalizePhone(it.phone!!) }
            .mapValues { (_, logs) -> logs.minByOrNull { it.createdAt }!! }

        val firstResponseMinutes = resolved.mapNotNull { conversation ->
            val claimedAt = conversation.claimedAt ?: return@mapNotNull null
            val firstReply = repliesByPhone[normalizePhone(conversation.phone)]
                ?.takeIf { !it.createdAt.isBefore(claimedAt) }
                ?: return@mapNotNull null
            ChronoUnit.MINUTES.between(claimedAt, firstReply.createdAt).toDouble()
        }
        val resolutionMinutes = resolved.mapNotNull { conversation ->
            val claimedAt = conversation.claimedAt ?: return@mapNotNull null
            val resolvedAt = conversation.resolvedAt ?: return@mapNotNull null
            ChronoUnit.MINUTES.between(claimedAt, resolvedAt).toDouble()
        }

        val reopenEvents = assignmentEvents.count { it.eventType == CrmAssignmentEventType.REOPEN }.toLong()
        val transferEvents = assignmentEvents.count { it.eventType == CrmAssignmentEventType.TRANSFER }.toLong()
        val botToHuman = botTraces.count { trace ->
            trace.details?.contains("escalate=") == true ||
                trace.details?.contains("intent=HUMAN_ESCALATION") == true
        }.toLong()
        val autoResolved = resolved.count { it.claimedAt == null }.toLong()
        val autoResolutionRate = if (resolved.isEmpty()) null else round2(autoResolved.toDouble() / resolved.size.toDouble())

        val now = LocalDateTime.now()
        val abandonmentCount = openQueue.count { conversation ->
            val anchor = conversation.lastInboundAt ?: conversation.updatedAt
            ChronoUnit.HOURS.between(anchor, now) >= properties.abandonmentPendingHours
        }.toLong()

        val slaFirstOk = firstResponseMinutes.count { it <= properties.firstResponseSlaMinutes }
        val slaResolutionOk = resolutionMinutes.count { it <= properties.resolutionSlaMinutes }
        val firstResponseWithinSlaRate = rate(slaFirstOk, firstResponseMinutes.size)
        val resolutionWithinSlaRate = rate(slaResolutionOk, resolutionMinutes.size)

        val ticketIds = ticketLinks.map { it.ticketId }.distinct()
        val linkedTickets = if (ticketIds.isEmpty()) emptyList() else ticketRepository.findAllById(ticketIds)
        val linkedTicketsSlaBreached = linkedTickets.count { CrmTicketLinkService.isSlaBreached(it) }.toLong()
        val linkByConversation = ticketLinks.associateBy { it.conversationId }
        val ticketById = linkedTickets.associateBy { it.id }

        val recurringPhones = resolved
            .groupingBy { normalizePhone(it.phone) }
            .eachCount()
            .count { it.value > 1 }
            .toLong()

        val placeBuckets = bucketConversations(resolved) { conversation ->
            placeLabel(conversation, linkByConversation, ticketById)
        }
        val categoryBuckets = bucketConversations(resolved) { conversation ->
            val ticket = linkByConversation[conversation.id]?.let { ticketById[it.ticketId] }
            ticket?.category ?: "Sin ticket vinculado"
        }
        val agentBuckets = bucketByAgent(resolved, assignmentEvents)

        val csat = csatSurveyService.buildSummary(from, to)

        return CrmMetricsSummaryDto(
            from = from,
            to = to,
            resolvedConversations = resolved.size.toLong(),
            pendingUnassigned = openQueue.size.toLong(),
            assignedActive = assignedActive.size.toLong(),
            reopenEvents = reopenEvents,
            transferEvents = transferEvents,
            avgFirstResponseMinutes = average(firstResponseMinutes),
            avgResolutionMinutes = average(resolutionMinutes),
            firstResponseWithinSlaRate = firstResponseWithinSlaRate,
            resolutionWithinSlaRate = resolutionWithinSlaRate,
            abandonmentCount = abandonmentCount,
            botToHumanTransfers = botToHuman,
            autoResolutionRate = autoResolutionRate,
            ticketsFromConversations = ticketLinks.size.toLong(),
            linkedTicketsSlaBreached = linkedTicketsSlaBreached,
            recurringCustomerPhones = recurringPhones,
            contactReasons = parseContactReasons(botTraces),
            byAgent = agentBuckets,
            byPlace = placeBuckets,
            byCategory = categoryBuckets,
            csatReference = CrmMetricsCsatReferenceDto(
                panelPath = "/crm/csat",
                responseRate = csat.responseRate,
                averageScore = csat.averageScore,
                answeredSurveys = csat.answered
            )
        )
    }

    fun buildAgentBreakdown(from: LocalDateTime, to: LocalDateTime): List<CrmMetricsAgentLoadDto> {
        val events = assignmentEventRepository.findByCreatedAtBetween(from, to)
        val assigned = conversationRepository.findByStatusIn(listOf(CrmConversationStatus.ASSIGNED))
        val agentIds = (
            events.mapNotNull { it.fromUserId } +
                events.mapNotNull { it.toUserId } +
                assigned.mapNotNull { it.assignedAgentId }
            ).distinct()

        return agentIds.sorted().map { agentId ->
            CrmMetricsAgentLoadDto(
                agentId = agentId,
                agentLabel = "Agente #$agentId",
                resolvedCount = events.count {
                    it.eventType == CrmAssignmentEventType.RESOLVE && it.fromUserId == agentId
                }.toLong(),
                activeAssignedCount = assigned.count { it.assignedAgentId == agentId }.toLong(),
                transferInCount = events.count {
                    it.eventType == CrmAssignmentEventType.TRANSFER && it.toUserId == agentId
                }.toLong(),
                transferOutCount = events.count {
                    it.eventType == CrmAssignmentEventType.TRANSFER && it.fromUserId == agentId
                }.toLong()
            )
        }
    }

    fun buildShiftHandoff(from: LocalDateTime, to: LocalDateTime): CrmMetricsShiftHandoffDto {
        val openQueue = conversationRepository.findByStatusIn(QUEUE_STATUSES)
        val events = assignmentEventRepository.findByCreatedAtBetween(from, to)
        val botTraces = auditLogRepository.findByActionAndCreatedAtBetween(
            WhatsAppAuditService.ACTION_BOT_TRACE,
            from,
            to
        )
        val resolved = conversationRepository.findByResolvedAtBetween(from, to)
        val now = to
        val excessiveWait = openQueue.mapNotNull { conversation ->
            val anchor = conversation.lastInboundAt ?: conversation.updatedAt
            val waitMinutes = ChronoUnit.MINUTES.between(anchor, now)
            if (waitMinutes < properties.excessiveWaitMinutes) return@mapNotNull null
            CrmMetricsExcessiveWaitDto(
                conversationId = conversation.id ?: return@mapNotNull null,
                phone = conversation.phone,
                waitMinutes = waitMinutes,
                status = conversation.status.name
            )
        }

        return CrmMetricsShiftHandoffDto(
            from = from,
            to = to,
            pendingUnassigned = openQueue.size.toLong(),
            reopenEvents = events.count { it.eventType == CrmAssignmentEventType.REOPEN }.toLong(),
            botToHumanTransfers = botTraces.count { it.details?.contains("escalate=") == true }.toLong(),
            resolvedInWindow = resolved.size.toLong(),
            excessiveWaitAlerts = excessiveWait.sortedByDescending { it.waitMinutes }.take(20)
        )
    }

    private fun bucketByAgent(
        resolved: List<CrmConversation>,
        events: List<com.dscorp.wispadmin.wispadmin.data.model.CrmAssignmentEvent>
    ): List<CrmMetricsSampleBucketDto> {
        val resolveByConversation = events
            .filter { it.eventType == CrmAssignmentEventType.RESOLVE }
            .associateBy { it.conversationId }
        val grouped = resolved.groupBy { conversation ->
            resolveByConversation[conversation.id]?.fromUserId?.toString() ?: "none"
        }
        return grouped.map { (key, items) ->
            val minutes = items.mapNotNull { conversation ->
                val claimedAt = conversation.claimedAt ?: return@mapNotNull null
                val resolvedAt = conversation.resolvedAt ?: return@mapNotNull null
                ChronoUnit.MINUTES.between(claimedAt, resolvedAt).toDouble()
            }
            CrmMetricsSampleBucketDto(
                key = key,
                label = if (key == "none") "Sin agente" else "Agente #$key",
                sampleSize = items.size.toLong(),
                avgFirstResponseMinutes = null,
                avgResolutionMinutes = average(minutes),
                resolvedCount = items.size.toLong()
            )
        }.sortedByDescending { it.sampleSize }
    }

    private fun bucketConversations(
        resolved: List<CrmConversation>,
        labelFn: (CrmConversation) -> String
    ): List<CrmMetricsSampleBucketDto> {
        return resolved
            .groupBy { labelFn(it) }
            .map { (label, items) ->
                val resolution = items.mapNotNull { conversation ->
                    val claimedAt = conversation.claimedAt ?: return@mapNotNull null
                    val resolvedAt = conversation.resolvedAt ?: return@mapNotNull null
                    ChronoUnit.MINUTES.between(claimedAt, resolvedAt).toDouble()
                }
                CrmMetricsSampleBucketDto(
                    key = label,
                    label = label,
                    sampleSize = items.size.toLong(),
                    avgFirstResponseMinutes = null,
                    avgResolutionMinutes = average(resolution),
                    resolvedCount = items.size.toLong()
                )
            }
            .sortedByDescending { it.sampleSize }
    }

    private fun placeLabel(
        conversation: CrmConversation,
        linkByConversation: Map<Long, com.dscorp.wispadmin.wispadmin.data.model.TicketConversationLink>,
        ticketById: Map<Int, AssistanceTicket>
    ): String {
        val ticket = linkByConversation[conversation.id]?.let { ticketById[it.ticketId] }
        return ticket?.placeName?.trim()?.takeIf { it.isNotEmpty() }
            ?: conversation.subscriptionId?.let { "Suscripción #$it" }
            ?: "Sin zona"
    }

    private fun parseContactReasons(
        traces: List<com.dscorp.wispadmin.wispadmin.data.model.WhatsAppAuditLog>
    ): List<CrmMetricsIntentCountDto> {
        return traces
            .mapNotNull { trace ->
                val details = trace.details ?: return@mapNotNull null
                INTENT_PATTERN.find(details)?.groupValues?.get(1)
            }
            .groupingBy { it }
            .eachCount()
            .map { CrmMetricsIntentCountDto(it.key, it.value.toLong()) }
            .sortedByDescending { it.count }
    }

    private fun normalizePhone(phone: String): String = CrmTicketLinkService.normalizePhone(phone)

    private fun average(values: List<Double>): Double? =
        values.takeIf { it.isNotEmpty() }?.average()?.let { round2(it) }

    private fun rate(numerator: Int, denominator: Int): Double? =
        if (denominator == 0) null else round2(numerator.toDouble() / denominator.toDouble())

    private fun round2(value: Double): Double = round(value * 100.0) / 100.0

    companion object {
        private val QUEUE_STATUSES = listOf(
            CrmConversationStatus.NEW,
            CrmConversationStatus.PENDING,
            CrmConversationStatus.REOPENED
        )
        private val OPERATOR_MESSAGE_TYPES = listOf(
            WhatsAppConversationService.MESSAGE_TYPE_OPERATOR_REPLY,
            WhatsAppConversationService.MESSAGE_TYPE_OPERATOR_MEDIA
        )
        private val INTENT_PATTERN = Regex("""intent=([^;]+)""")
    }
}
