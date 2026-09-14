package com.dscorp.wispadmin.wispadmin.service.whatsapp

import java.time.LocalDateTime

enum class WhatsAppInboxView {
    QUEUE,
    MINE,
    TEAM,
    RECEIPTS,
    ADVISORS,
    RESOLVED,
    ALL;

    companion object {
        fun fromParam(raw: String?): WhatsAppInboxView {
            if (raw.isNullOrBlank()) return ALL
            return runCatching { valueOf(raw.trim().uppercase()) }.getOrDefault(ALL)
        }
    }
}

object WhatsAppInboxViewPolicy {

    fun belongsInUnattendedQueue(
        status: String?,
        lastInboundAt: LocalDateTime?,
        lastOutboundAt: LocalDateTime?
    ): Boolean {
        if (lastInboundAt == null) return false
        val normalized = status?.trim()?.uppercase().orEmpty()
        val unattended =
            normalized.isEmpty() ||
                normalized == "NEW" ||
                normalized == "PENDING" ||
                normalized == "REOPENED"
        if (!unattended) return false
        if (lastOutboundAt == null) return true
        return lastInboundAt.isAfter(lastOutboundAt)
    }

    fun hasPendingReceipt(
        status: String?,
        resolvedAt: LocalDateTime?,
        latestMediaAt: LocalDateTime?
    ): Boolean {
        if (latestMediaAt == null) return false
        val normalized = status?.trim()?.uppercase().orEmpty()
        if (normalized == "RESOLVED") return false
        if (resolvedAt == null) return true
        return latestMediaAt.isAfter(resolvedAt)
    }

    fun hasPendingAdvisorRequest(
        status: String?,
        resolvedAt: LocalDateTime?,
        latestAdvisorRequestAt: LocalDateTime?
    ): Boolean {
        if (latestAdvisorRequestAt == null) return false
        val normalized = status?.trim()?.uppercase().orEmpty()
        if (normalized == "RESOLVED") return false
        if (resolvedAt == null) return true
        return latestAdvisorRequestAt.isAfter(resolvedAt)
    }

    fun matchesView(
        view: WhatsAppInboxView,
        status: String?,
        assignedAgentId: Int?,
        currentAgentId: Int?,
        lastInboundAt: LocalDateTime?,
        lastOutboundAt: LocalDateTime?,
        hasPendingReceipt: Boolean,
        hasPendingAdvisorRequest: Boolean = false
    ): Boolean {
        val normalized = (status ?: "NEW").trim().uppercase()
        return when (view) {
            WhatsAppInboxView.ALL -> true
            WhatsAppInboxView.RESOLVED -> normalized == "RESOLVED"
            WhatsAppInboxView.RECEIPTS -> hasPendingReceipt
            WhatsAppInboxView.ADVISORS -> hasPendingAdvisorRequest
            WhatsAppInboxView.MINE ->
                normalized == "ASSIGNED" &&
                    currentAgentId != null &&
                    assignedAgentId != null &&
                    assignedAgentId == currentAgentId
            WhatsAppInboxView.TEAM ->
                normalized == "ASSIGNED" &&
                    (currentAgentId == null ||
                        assignedAgentId == null ||
                        assignedAgentId != currentAgentId)
            WhatsAppInboxView.QUEUE ->
                belongsInUnattendedQueue(status, lastInboundAt, lastOutboundAt)
        }
    }
}
