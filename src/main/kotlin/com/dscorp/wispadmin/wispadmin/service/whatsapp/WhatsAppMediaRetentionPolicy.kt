package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppRetentionProperties
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversationStatus
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog

data class WhatsAppMediaRetentionSettings(
    val paymentProofDays: Long = 730,
    val generalInboundDays: Long = 90,
    val unresolvedInboundMaxDays: Long = 365,
    val outboundDays: Long = 60,
    val outboundFailedDays: Long = 30,
    val minAgeDays: Long = 7,
) {
    companion object {
        fun from(properties: WhatsAppRetentionProperties) = WhatsAppMediaRetentionSettings(
            paymentProofDays = properties.paymentProofDays,
            generalInboundDays = properties.generalInboundDays,
            unresolvedInboundMaxDays = properties.unresolvedInboundMaxDays,
            outboundDays = properties.outboundDays,
            outboundFailedDays = properties.outboundFailedDays,
            minAgeDays = properties.minAgeDays,
        )
    }
}

data class InboundRetentionContext(
    val crmStatus: CrmConversationStatus?,
    val resolvedAt: java.time.LocalDateTime?,
    val hasPendingReceipt: Boolean,
    val latestPaymentProofMediaAt: java.time.LocalDateTime? = null,
)

object WhatsAppMediaRetentionPolicy {

    fun shouldPurgeInbound(
        inbound: WhatsAppInboundMessage,
        context: InboundRetentionContext,
        now: java.time.LocalDateTime,
        settings: WhatsAppMediaRetentionSettings,
    ): Boolean {
        if (inbound.mediaStoredPath.isNullOrBlank() || inbound.mediaPurgedAt != null) {
            return false
        }
        if (!inbound.createdAt.isBefore(now.minusDays(settings.minAgeDays))) {
            return false
        }
        val isProof = WhatsAppThreadMessageMapper.inboundIsPaymentProof(inbound)
        if (isProof) {
            if (context.crmStatus != CrmConversationStatus.RESOLVED) {
                return false
            }
            if (context.hasPendingReceipt || hasPostResolvePaymentProof(context)) {
                return false
            }
            return inbound.createdAt.isBefore(now.minusDays(settings.paymentProofDays))
        }
        if (context.crmStatus == CrmConversationStatus.RESOLVED && context.resolvedAt != null) {
            if (!now.isAfter(context.resolvedAt.plusDays(settings.generalInboundDays))) {
                return false
            }
            return true
        }
        return inbound.createdAt.isBefore(now.minusDays(settings.unresolvedInboundMaxDays))
    }

    private fun hasPostResolvePaymentProof(context: InboundRetentionContext): Boolean {
        val resolvedAt = context.resolvedAt ?: return false
        val latest = context.latestPaymentProofMediaAt ?: return false
        return latest.isAfter(resolvedAt)
    }

    fun shouldPurgeOutbound(
        log: WhatsAppMessageLog,
        now: java.time.LocalDateTime,
        settings: WhatsAppMediaRetentionSettings,
    ): Boolean {
        if (log.mediaStoredPath.isNullOrBlank() || log.mediaPurgedAt != null) {
            return false
        }
        if (!log.createdAt.isBefore(now.minusDays(settings.minAgeDays))) {
            return false
        }
        val failed = log.status.equals("FAILED", ignoreCase = true)
        if (failed) {
            val anchor = log.failedAt ?: log.createdAt
            return now.isAfter(anchor.plusDays(settings.outboundFailedDays))
        }
        val anchor = log.sentAt ?: log.createdAt
        return now.isAfter(anchor.plusDays(settings.outboundDays))
    }
}
