package com.dscorp.wispadmin.wispadmin.service.whatsapp

import java.time.LocalDateTime

/**
 * Single source of truth for CRM "Por atender" (unattended queue) membership.
 *
 * A conversation belongs in the queue only when:
 * - CRM status is unattended (NEW / PENDING / REOPENED / missing), and
 * - the last customer inbound is newer than the last outbound (HSM/agent/bot).
 */
object CrmInboxQueuePolicy {

    private val UNATTENDED_STATUSES = setOf("NEW", "PENDING", "REOPENED")

    fun belongsInUnattendedQueue(
        status: String?,
        lastInboundAt: LocalDateTime?,
        lastOutboundAt: LocalDateTime?,
    ): Boolean {
        if (lastInboundAt == null) return false
        val normalized = status?.trim()?.uppercase().orEmpty()
        val unattended = normalized.isEmpty() || normalized in UNATTENDED_STATUSES
        if (!unattended) return false
        if (lastOutboundAt == null) return true
        return lastInboundAt.isAfter(lastOutboundAt)
    }
}
