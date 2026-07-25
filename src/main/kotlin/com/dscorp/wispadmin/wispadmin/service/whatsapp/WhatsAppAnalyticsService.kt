package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.repository.PaymentRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class WhatsAppAnalyticsService(
    private val messageLogRepository: WhatsAppMessageLogRepository,
    private val inboundMessageRepository: WhatsAppInboundMessageRepository,
    private val paymentRepository: PaymentRepository
) {

    fun overview(from: LocalDateTime, to: LocalDateTime, windowDays: Int = 7, templateCode: String? = null): WhatsAppAnalyticsOverview {
        val logs = messageLogRepository.findByCreatedAtBetween(from, to)
            .filter { templateCode.isNullOrBlank() || it.messageType == templateCode }
        val sent = logs.count { it.status == WhatsAppTemplateDeliveryService.STATUS_SENT }
        val delivered = logs.count { it.deliveredAt != null }
        val read = logs.count { it.readAt != null }
        val failed = logs.count { it.failedAt != null || it.deliveryStatus == "failed" }
        val skipped = logs.count { it.status == WhatsAppTemplateDeliveryService.STATUS_SKIPPED }
        val withoutMetaId = logs.count {
            it.status == WhatsAppTemplateDeliveryService.STATUS_SENT && it.metaMessageId.isNullOrBlank()
        }

        val inbound = inboundMessageRepository.findByCreatedAtBetween(from, to)
        val responded = inbound.size

        val conversion = conversion(from, to, windowDays, templateCode)

        return WhatsAppAnalyticsOverview(
            from = from,
            to = to,
            sent = sent,
            delivered = delivered,
            read = read,
            failed = failed,
            skipped = skipped,
            responded = responded,
            paid = conversion.converted,
            sentWithoutMetaMessageId = withoutMetaId,
            deliveryRate = rate(delivered, sent),
            readRate = rate(read, sent),
            responseRate = rate(responded, sent),
            conversionRate = conversion.conversionRate,
            recoveredAmount = conversion.recoveredAmount,
            periodDays = windowDays
        )
    }

    fun campaigns(from: LocalDateTime, to: LocalDateTime, windowDays: Int = 7): List<WhatsAppCampaignAnalytics> {
        val logs = messageLogRepository.findByCreatedAtBetween(from, to)
            .filter { !it.campaignId.isNullOrBlank() }
        val inbound = inboundMessageRepository.findByCreatedAtBetween(from, to)

        return logs.groupBy { it.campaignId!! }.map { (campaignId, entries) ->
            val sent = entries.count { it.status == WhatsAppTemplateDeliveryService.STATUS_SENT }
            val phones = entries.mapNotNull { it.phone }.toSet()
            val startedAt = entries.minOfOrNull { it.createdAt }
            val responded = if (startedAt == null) {
                0
            } else {
                inbound.count { msg ->
                    phones.contains(msg.phone) && !msg.createdAt.isBefore(startedAt)
                }
            }
            val conversionAmount = computeRecoveredAmount(entries, windowDays)
            val paid = if (conversionAmount > 0.0) {
                entries.count { entry ->
                    entry.subscriptionId != null && hasPaidInWindow(entry, windowDays)
                }.coerceAtMost(sent)
            } else {
                0
            }

            val templateCode = entries.firstOrNull()?.messageType
            WhatsAppCampaignAnalytics(
                campaignId = campaignId,
                templateCode = templateCode,
                templateLabel = templateCode?.let { labelForTemplate(it) },
                operatorUsername = entries.firstOrNull()?.operatorUsername,
                sent = sent,
                delivered = entries.count { it.deliveredAt != null },
                read = entries.count { it.readAt != null },
                failed = entries.count { it.failedAt != null },
                responded = responded,
                paid = paid,
                startedAt = startedAt,
                deliveryRate = rate(entries.count { it.deliveredAt != null }, sent),
                conversionAmount = conversionAmount
            )
        }.sortedByDescending { it.startedAt }
    }

    fun campaignDetail(campaignId: String, windowDays: Int = 7): WhatsAppCampaignDetail? {
        val logs = messageLogRepository.findByCampaignId(campaignId)
        if (logs.isEmpty()) return null
        val from = logs.minOf { it.createdAt }
        val to = logs.maxOf { it.createdAt }.plusSeconds(1)
        val summary = campaigns(from, to, windowDays).firstOrNull { it.campaignId == campaignId }

        return WhatsAppCampaignDetail(
            summary = summary,
            messages = logs.sortedByDescending { it.createdAt }.map { it.toAnalyticsRow() }
        )
    }

    fun conversion(from: LocalDateTime, to: LocalDateTime, windowDays: Int = 7, templateCode: String? = null): WhatsAppConversionAnalytics {
        val logs = messageLogRepository.findByCreatedAtBetween(from, to)
            .filter { it.status == WhatsAppTemplateDeliveryService.STATUS_SENT && it.subscriptionId != null }
            .filter { templateCode.isNullOrBlank() || it.messageType == templateCode }

        var converted = 0
        var recoveredAmount = 0.0
        val byTemplate = linkedMapOf<String, TemplateConversionAccumulator>()

        logs.forEach { log ->
            val subscriptionId = log.subscriptionId ?: return@forEach
            val templateCode = log.messageType
            val bucket = byTemplate.getOrPut(templateCode) {
                TemplateConversionAccumulator(templateCode, labelForTemplate(templateCode))
            }
            bucket.sent++

            if (hasPaidInWindow(log, windowDays)) {
                converted++
                bucket.converted++
                val amount = recoveredForLog(log, windowDays)
                recoveredAmount += amount
                bucket.recoveredAmount += amount
            }
        }

        val sent = logs.size
        return WhatsAppConversionAnalytics(
            from = from,
            to = to,
            windowDays = windowDays,
            sent = sent,
            converted = converted,
            conversionRate = rate(converted, sent),
            recoveredAmount = recoveredAmount,
            byTemplate = byTemplate.values.map {
                WhatsAppTemplateConversionAnalytics(
                    templateCode = it.templateCode,
                    templateLabel = it.templateLabel,
                    sent = it.sent,
                    converted = it.converted,
                    conversionRate = rate(it.converted, it.sent),
                    recoveredAmount = it.recoveredAmount
                )
            }.sortedByDescending { it.sent }
        )
    }

    private fun computeRecoveredAmount(
        entries: List<com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog>,
        windowDays: Int
    ): Double {
        return entries
            .filter { it.status == WhatsAppTemplateDeliveryService.STATUS_SENT }
            .sumOf { recoveredForLog(it, windowDays) }
    }

    private fun recoveredForLog(
        log: com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog,
        windowDays: Int
    ): Double {
        val subscriptionId = log.subscriptionId ?: return 0.0
        val windowEnd = log.createdAt.plusDays(windowDays.toLong())
        return paymentRepository.findBySubscriptionIdOrderByBillingDateDatetimeDesc(subscriptionId)
            .filter { payment ->
                payment.paid &&
                    payment.paymentDateDatetime != null &&
                    !payment.paymentDateDatetime!!.isBefore(log.createdAt) &&
                    !payment.paymentDateDatetime!!.isAfter(windowEnd)
            }
            .sumOf { it.amountPaid ?: it.amountToPay }
    }

    private fun hasPaidInWindow(
        log: com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog,
        windowDays: Int
    ): Boolean {
        return recoveredForLog(log, windowDays) > 0.0
    }

    private fun labelForTemplate(templateCode: String): String? {
        return runCatching {
            WhatsAppTemplateCatalog.getByCodeString(templateCode).label
        }.getOrNull()
    }

    private fun rate(numerator: Int, denominator: Int): Double {
        if (denominator == 0) return 0.0
        return (numerator.toDouble() / denominator.toDouble()) * 100.0
    }

    private fun com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog.toAnalyticsRow() =
        WhatsAppMessageAnalyticsRow(
            id = id,
            phone = phone,
            messageType = messageType,
            status = status,
            deliveryStatus = deliveryStatus,
            metaMessageId = metaMessageId,
            sentAt = sentAt,
            deliveredAt = deliveredAt,
            readAt = readAt,
            failedAt = failedAt,
            createdAt = createdAt
        )

    private data class TemplateConversionAccumulator(
        val templateCode: String,
        val templateLabel: String?,
        var sent: Int = 0,
        var converted: Int = 0,
        var recoveredAmount: Double = 0.0
    )

    data class WhatsAppAnalyticsOverview(
        val from: LocalDateTime,
        val to: LocalDateTime,
        val sent: Int,
        val delivered: Int,
        val read: Int,
        val failed: Int,
        val skipped: Int,
        val responded: Int,
        val paid: Int,
        val sentWithoutMetaMessageId: Int,
        val deliveryRate: Double,
        val readRate: Double,
        val responseRate: Double,
        val conversionRate: Double,
        val recoveredAmount: Double,
        val periodDays: Int
    )

    data class WhatsAppCampaignAnalytics(
        val campaignId: String,
        val templateCode: String?,
        val templateLabel: String?,
        val operatorUsername: String?,
        val sent: Int,
        val delivered: Int,
        val read: Int,
        val failed: Int,
        val responded: Int,
        val paid: Int,
        val startedAt: LocalDateTime?,
        val deliveryRate: Double,
        val conversionAmount: Double
    )

    data class WhatsAppCampaignDetail(
        val summary: WhatsAppCampaignAnalytics?,
        val messages: List<WhatsAppMessageAnalyticsRow>
    )

    data class WhatsAppMessageAnalyticsRow(
        val id: Int?,
        val phone: String?,
        val messageType: String,
        val status: String,
        val deliveryStatus: String?,
        val metaMessageId: String?,
        val sentAt: LocalDateTime?,
        val deliveredAt: LocalDateTime?,
        val readAt: LocalDateTime?,
        val failedAt: LocalDateTime?,
        val createdAt: LocalDateTime
    )

    data class WhatsAppConversionAnalytics(
        val from: LocalDateTime,
        val to: LocalDateTime,
        val windowDays: Int,
        val sent: Int,
        val converted: Int,
        val conversionRate: Double,
        val recoveredAmount: Double,
        val byTemplate: List<WhatsAppTemplateConversionAnalytics>
    )

    data class WhatsAppTemplateConversionAnalytics(
        val templateCode: String,
        val templateLabel: String?,
        val sent: Int,
        val converted: Int,
        val conversionRate: Double,
        val recoveredAmount: Double
    )
}
