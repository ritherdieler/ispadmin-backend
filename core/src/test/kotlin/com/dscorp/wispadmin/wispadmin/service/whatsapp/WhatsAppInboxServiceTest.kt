package com.dscorp.wispadmin.wispadmin.service.whatsapp

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * TDD suite for "Por atender" queue membership.
 * Outbound HSM / agent messages must not keep chats in the unattended queue.
 */
class WhatsAppInboxServiceTest {

    private val t0 = LocalDateTime.parse("2026-08-06T12:00:00")

    @Test
    fun `Test 1 outbound HSM template alone must not appear in Por atender`() {
        assertFalse(
            CrmInboxQueuePolicy.belongsInUnattendedQueue(
                status = "NEW",
                lastInboundAt = null,
                lastOutboundAt = t0,
            ),
        )
        assertFalse(
            CrmInboxQueuePolicy.belongsInUnattendedQueue(
                status = "PENDING",
                lastInboundAt = t0.minusHours(2),
                lastOutboundAt = t0, // last activity is outbound template
            ),
        )
    }

    @Test
    fun `Test 2 customer inbound reply after template moves conversation into Por atender`() {
        assertTrue(
            CrmInboxQueuePolicy.belongsInUnattendedQueue(
                status = "PENDING",
                lastInboundAt = t0,
                lastOutboundAt = t0.minusMinutes(30),
            ),
        )
        assertTrue(
            CrmInboxQueuePolicy.belongsInUnattendedQueue(
                status = "REOPENED",
                lastInboundAt = t0,
                lastOutboundAt = t0.minusHours(1),
            ),
        )
    }

    @Test
    fun `Test 3 agent assigned reply leaves Por atender toward Mias`() {
        assertFalse(
            CrmInboxQueuePolicy.belongsInUnattendedQueue(
                status = "ASSIGNED",
                lastInboundAt = t0,
                lastOutboundAt = t0.minusMinutes(5),
            ),
        )
        // Even if still PENDING, once agent/outbound is last, leave queue
        assertFalse(
            CrmInboxQueuePolicy.belongsInUnattendedQueue(
                status = "PENDING",
                lastInboundAt = t0.minusMinutes(10),
                lastOutboundAt = t0,
            ),
        )
    }
}
