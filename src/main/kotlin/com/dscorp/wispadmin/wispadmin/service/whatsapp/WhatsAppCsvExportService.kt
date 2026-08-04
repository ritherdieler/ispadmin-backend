package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.dto.WhatsAppCampaignSummaryDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppInboundMessageDto
import com.dscorp.wispadmin.wispadmin.dto.WhatsAppMessageLogDto
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class WhatsAppCsvExportService {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun exportLogs(logs: List<WhatsAppMessageLogDto>): String {
        val header = listOf(
            "id", "phone", "templateCode", "status", "deliveryStatus", "metaMessageId",
            "campaignId", "operatorName", "paymentId", "subscriptionId",
            "sentAt", "deliveredAt", "readAt", "failedAt", "createdAt", "errorMessage"
        )
        val rows = logs.map { log ->
            listOf(
                log.id,
                log.phone,
                log.templateCode,
                log.status,
                log.deliveryStatus,
                log.metaMessageId,
                log.campaignId,
                log.operatorName,
                log.paymentId,
                log.subscriptionId,
                log.sentAt?.format(formatter),
                log.deliveredAt?.format(formatter),
                log.readAt?.format(formatter),
                log.failedAt?.format(formatter),
                log.createdAt.format(formatter),
                log.errorMessage
            )
        }
        return toCsv(header, rows)
    }

    fun exportCampaigns(campaigns: List<WhatsAppCampaignSummaryDto>): String {
        val header = listOf(
            "id", "templateCode", "templateLabel", "sent", "delivered", "read",
            "failed", "responded", "paid", "operatorName", "createdAt", "conversionAmount"
        )
        val rows = campaigns.map { campaign ->
            listOf(
                campaign.id,
                campaign.templateCode,
                campaign.templateLabel,
                campaign.sent,
                campaign.delivered,
                campaign.read,
                campaign.failed,
                campaign.responded,
                campaign.paid,
                campaign.operatorName,
                formatCampaignCreatedAt(campaign.createdAt),
                campaign.conversionAmount
            )
        }
        return toCsv(header, rows)
    }

    fun exportInbound(messages: List<WhatsAppInboundMessageDto>): String {
        val header = listOf(
            "id", "phone", "clientName", "messageType", "body", "buttonReplyId",
            "buttonReplyTitle", "identified", "autoReplySent", "subscriptionId", "createdAt"
        )
        val rows = messages.map { message ->
            listOf(
                message.id,
                message.phone,
                message.clientName,
                message.messageType,
                message.body,
                message.buttonReplyId,
                message.buttonReplyTitle,
                message.identified,
                message.autoReplySent,
                message.subscriptionId,
                message.createdAt.format(formatter)
            )
        }
        return toCsv(header, rows)
    }

    private fun formatCampaignCreatedAt(createdAt: String?): String? {
        if (createdAt.isNullOrBlank()) return createdAt
        return runCatching { LocalDateTime.parse(createdAt).format(formatter) }.getOrDefault(createdAt)
    }

    private fun toCsv(header: List<String>, rows: List<List<Any?>>): String {
        val builder = StringBuilder()
        builder.appendLine(header.joinToString(",") { escapeCsv(it) })
        rows.forEach { row ->
            builder.appendLine(row.joinToString(",") { escapeCsv(it?.toString()) })
        }
        return builder.toString()
    }

    private fun escapeCsv(value: String?): String {
        val safe = value ?: ""
        return if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
            "\"${safe.replace("\"", "\"\"")}\""
        } else {
            safe
        }
    }
}
