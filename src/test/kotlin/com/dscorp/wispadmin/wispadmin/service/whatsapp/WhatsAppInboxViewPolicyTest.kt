package com.dscorp.wispadmin.wispadmin.service.whatsapp

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class WhatsAppInboxViewPolicyTest {

    private val t0 = LocalDateTime.of(2026, 8, 1, 10, 0)
    private val t1 = LocalDateTime.of(2026, 8, 2, 10, 0)
    private val t2 = LocalDateTime.of(2026, 8, 3, 10, 0)

    @Test
    fun `pending receipt is receipt after last resolve`() {
        assertTrue(
            WhatsAppInboxViewPolicy.hasPendingReceipt(
                status = "NEW",
                resolvedAt = null,
                latestMediaAt = t1
            )
        )
        assertFalse(
            WhatsAppInboxViewPolicy.hasPendingReceipt(
                status = "RESOLVED",
                resolvedAt = t1,
                latestMediaAt = t0
            )
        )
        assertFalse(
            WhatsAppInboxViewPolicy.hasPendingReceipt(
                status = "REOPENED",
                resolvedAt = t1,
                latestMediaAt = t0
            )
        )
        assertTrue(
            WhatsAppInboxViewPolicy.hasPendingReceipt(
                status = "REOPENED",
                resolvedAt = t1,
                latestMediaAt = t2
            )
        )
    }

    @Test
    fun `text after resolve does not keep conversation in receipts`() {
        val matches = WhatsAppInboxViewPolicy.matchesView(
            view = WhatsAppInboxView.RECEIPTS,
            status = "REOPENED",
            assignedAgentId = null,
            currentAgentId = 7,
            lastInboundAt = t2,
            lastOutboundAt = t0,
            hasPendingReceipt = WhatsAppInboxViewPolicy.hasPendingReceipt(
                status = "REOPENED",
                resolvedAt = t1,
                latestMediaAt = t0
            )
        )
        assertFalse(matches)
    }

    @Test
    fun `receipt after resolve keeps conversation in receipts when reopened`() {
        val matches = WhatsAppInboxViewPolicy.matchesView(
            view = WhatsAppInboxView.RECEIPTS,
            status = "REOPENED",
            assignedAgentId = null,
            currentAgentId = 7,
            lastInboundAt = t2,
            lastOutboundAt = t0,
            hasPendingReceipt = WhatsAppInboxViewPolicy.hasPendingReceipt(
                status = "REOPENED",
                resolvedAt = t1,
                latestMediaAt = t2
            )
        )
        assertTrue(matches)
    }

    @Test
    fun `queue requires inbound newer than outbound`() {
        assertTrue(
            WhatsAppInboxViewPolicy.belongsInUnattendedQueue("NEW", t2, t1)
        )
        assertFalse(
            WhatsAppInboxViewPolicy.belongsInUnattendedQueue("NEW", t1, t2)
        )
        assertFalse(
            WhatsAppInboxViewPolicy.belongsInUnattendedQueue("ASSIGNED", t2, t1)
        )
    }

    @Test
    fun `pending advisor request never resolved or after last resolve`() {
        assertTrue(
            WhatsAppInboxViewPolicy.hasPendingAdvisorRequest(
                status = "PENDING",
                resolvedAt = null,
                latestAdvisorRequestAt = t1
            )
        )
        assertTrue(
            WhatsAppInboxViewPolicy.hasPendingAdvisorRequest(
                status = "REOPENED",
                resolvedAt = t1,
                latestAdvisorRequestAt = t2
            )
        )
        assertFalse(
            WhatsAppInboxViewPolicy.hasPendingAdvisorRequest(
                status = "REOPENED",
                resolvedAt = t1,
                latestAdvisorRequestAt = t0
            )
        )
        assertFalse(
            WhatsAppInboxViewPolicy.hasPendingAdvisorRequest(
                status = "RESOLVED",
                resolvedAt = t1,
                latestAdvisorRequestAt = t2
            )
        )
        assertFalse(
            WhatsAppInboxViewPolicy.hasPendingAdvisorRequest(
                status = "PENDING",
                resolvedAt = null,
                latestAdvisorRequestAt = null
            )
        )
    }

    @Test
    fun `advisor after resolve keeps conversation in advisors view`() {
        val matches = WhatsAppInboxViewPolicy.matchesView(
            view = WhatsAppInboxView.ADVISORS,
            status = "PENDING",
            assignedAgentId = null,
            currentAgentId = 7,
            lastInboundAt = t2,
            lastOutboundAt = t0,
            hasPendingReceipt = false,
            hasPendingAdvisorRequest = WhatsAppInboxViewPolicy.hasPendingAdvisorRequest(
                status = "PENDING",
                resolvedAt = t1,
                latestAdvisorRequestAt = t2
            )
        )
        assertTrue(matches)
    }

    @Test
    fun `advisor before resolve does not keep conversation in advisors view`() {
        val matches = WhatsAppInboxViewPolicy.matchesView(
            view = WhatsAppInboxView.ADVISORS,
            status = "REOPENED",
            assignedAgentId = null,
            currentAgentId = 7,
            lastInboundAt = t2,
            lastOutboundAt = t0,
            hasPendingReceipt = false,
            hasPendingAdvisorRequest = WhatsAppInboxViewPolicy.hasPendingAdvisorRequest(
                status = "REOPENED",
                resolvedAt = t1,
                latestAdvisorRequestAt = t0
            )
        )
        assertFalse(matches)
    }
}
