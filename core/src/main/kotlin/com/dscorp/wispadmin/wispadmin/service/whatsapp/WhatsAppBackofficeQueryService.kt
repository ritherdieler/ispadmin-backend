package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppInboundMessageDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMessageLogDto
import com.dscorp.wispadmin.wispadmin.dto.toDto
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class WhatsAppLogsFilter(
    val templateCode: String? = null,
    val status: String? = null,
    val deliveryStatus: String? = null,
    val dateFrom: LocalDateTime? = null,
    val dateTo: LocalDateTime? = null,
    val phone: String? = null,
    val limit: Int = 50
)

data class WhatsAppInboundFilter(
    val phone: String? = null,
    val search: String? = null,
    val dateFrom: LocalDateTime? = null,
    val dateTo: LocalDateTime? = null,
    val limit: Int = 50
)

@Service
class WhatsAppBackofficeQueryService(
    private val messageLogRepository: WhatsAppMessageLogRepository,
    private val inboundMessageRepository: WhatsAppInboundMessageRepository,
    private val subscriptionRepository: SubscriptionRepository
) {

    fun listLogs(filter: WhatsAppLogsFilter): List<WhatsAppMessageLogDto> {
        val base = if (filter.dateFrom != null && filter.dateTo != null) {
            messageLogRepository.findByCreatedAtBetween(filter.dateFrom, filter.dateTo)
        } else {
            messageLogRepository.findTop50ByOrderByCreatedAtDesc()
        }

        return base.asSequence()
            .filter { log -> filter.templateCode.isNullOrBlank() || log.messageType.equals(filter.templateCode, ignoreCase = true) }
            .filter { log -> filter.status.isNullOrBlank() || log.status.equals(filter.status, ignoreCase = true) }
            .filter { log -> filter.deliveryStatus.isNullOrBlank() || log.deliveryStatus.equals(filter.deliveryStatus, ignoreCase = true) }
            .filter { log -> filter.phone.isNullOrBlank() || log.phone?.contains(filter.phone!!) == true }
            .sortedByDescending { it.createdAt }
            .take(filter.limit.coerceIn(1, 500))
            .map { it.toDto() }
            .toList()
    }

    fun listInbound(filter: WhatsAppInboundFilter): List<WhatsAppInboundMessageDto> {
        val base = if (filter.dateFrom != null && filter.dateTo != null) {
            inboundMessageRepository.findByCreatedAtBetween(filter.dateFrom, filter.dateTo)
        } else {
            inboundMessageRepository.findTop50ByOrderByCreatedAtDesc()
        }

        val subscriptionNames = resolveSubscriptionNames(base)

        return base.asSequence()
            .filter { msg -> filter.phone.isNullOrBlank() || msg.phone.contains(filter.phone!!) }
            .filter { msg ->
                filter.search.isNullOrBlank() ||
                    msg.messageText?.contains(filter.search!!, ignoreCase = true) == true ||
                    msg.buttonReplyTitle?.contains(filter.search!!, ignoreCase = true) == true ||
                    msg.phone.contains(filter.search!!)
            }
            .sortedByDescending { it.createdAt }
            .take(filter.limit.coerceIn(1, 500))
            .map { it.toDto(clientName = subscriptionNames[it.subscriptionId]) }
            .toList()
    }

    fun resolveDateRange(
        dateFrom: String?,
        dateTo: String?,
        periodDays: Int?
    ): Pair<LocalDateTime, LocalDateTime> {
        val zone = ZoneId.of("America/Lima")
        val end = if (dateTo.isNullOrBlank()) {
            LocalDate.now(zone).plusDays(1).atStartOfDay()
        } else {
            LocalDate.parse(dateTo).plusDays(1).atStartOfDay()
        }
        val start = if (dateFrom.isNullOrBlank()) {
            end.minusDays((periodDays ?: 7).coerceAtLeast(1).toLong())
        } else {
            LocalDate.parse(dateFrom).atStartOfDay()
        }
        return start to end
    }

    private fun resolveSubscriptionNames(messages: List<WhatsAppInboundMessage>): Map<Int?, String?> {
        val ids = messages.mapNotNull { it.subscriptionId }.distinct()
        if (ids.isEmpty()) return emptyMap()
        return subscriptionRepository.findAllById(ids).associate { subscription ->
            subscription.id to subscription.getFullName()
        }
    }
}
