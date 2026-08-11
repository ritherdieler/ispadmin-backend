package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.CrmConversationStatus
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class WhatsAppMediaRetentionPolicyTest {

    private val settings = WhatsAppMediaRetentionSettings()
    private val now = LocalDateTime.of(2026, 8, 10, 12, 0)

    @Test
    fun `payment proof younger than 730 days is kept when resolved`() {
        val inbound = inboundProof(createdAt = now.minusDays(400))
        val ctx = resolvedContext(hasPendingReceipt = false)
        assertFalse(WhatsAppMediaRetentionPolicy.shouldPurgeInbound(inbound, ctx, now, settings))
    }

    @Test
    fun `payment proof older than 730 days is purged when resolved and no pending receipt`() {
        val inbound = inboundProof(createdAt = now.minusDays(731))
        val ctx = resolvedContext(hasPendingReceipt = false)
        assertTrue(WhatsAppMediaRetentionPolicy.shouldPurgeInbound(inbound, ctx, now, settings))
    }

    @Test
    fun `payment proof is never purged while conversation not resolved`() {
        val inbound = inboundProof(createdAt = now.minusDays(800))
        val ctx = InboundRetentionContext(
            crmStatus = CrmConversationStatus.ASSIGNED,
            resolvedAt = null,
            hasPendingReceipt = false
        )
        assertFalse(WhatsAppMediaRetentionPolicy.shouldPurgeInbound(inbound, ctx, now, settings))
    }

    @Test
    fun `payment proof is never purged while pending receipt`() {
        val inbound = inboundProof(createdAt = now.minusDays(800))
        val ctx = resolvedContext(hasPendingReceipt = true).copy(
            latestPaymentProofMediaAt = now.minusDays(1)
        )
        assertFalse(WhatsAppMediaRetentionPolicy.shouldPurgeInbound(inbound, ctx, now, settings))
    }

    @Test
    fun `payment proof is kept when new proof arrived after resolve`() {
        val inbound = inboundProof(createdAt = now.minusDays(800))
        val resolvedAt = now.minusDays(10)
        val ctx = InboundRetentionContext(
            crmStatus = CrmConversationStatus.RESOLVED,
            resolvedAt = resolvedAt,
            hasPendingReceipt = false,
            latestPaymentProofMediaAt = now.minusDays(1),
        )
        assertFalse(WhatsAppMediaRetentionPolicy.shouldPurgeInbound(inbound, ctx, now, settings))
    }

    @Test
    fun `inbound younger than min age is never purged`() {
        val inbound = inboundAudio(createdAt = now.minusDays(3))
        val ctx = resolvedContext(resolvedAt = now.minusDays(100), hasPendingReceipt = false)
        assertFalse(WhatsAppMediaRetentionPolicy.shouldPurgeInbound(inbound, ctx, now, settings))
    }

    @Test
    fun `general inbound is purged 90 days after resolve`() {
        val resolvedAt = now.minusDays(91)
        val inbound = inboundAudio(createdAt = resolvedAt.minusDays(1))
        val ctx = resolvedContext(resolvedAt = resolvedAt, hasPendingReceipt = false)
        assertTrue(WhatsAppMediaRetentionPolicy.shouldPurgeInbound(inbound, ctx, now, settings))
    }

    @Test
    fun `unresolved general inbound is purged after 365 days`() {
        val inbound = inboundAudio(createdAt = now.minusDays(366))
        val ctx = InboundRetentionContext(
            crmStatus = CrmConversationStatus.PENDING,
            resolvedAt = null,
            hasPendingReceipt = false
        )
        assertTrue(WhatsAppMediaRetentionPolicy.shouldPurgeInbound(inbound, ctx, now, settings))
    }

    @Test
    fun `successful outbound is purged after 60 days`() {
        val log = outboundLog(
            createdAt = now.minusDays(70),
            sentAt = now.minusDays(61),
            status = "SENT"
        )
        assertTrue(WhatsAppMediaRetentionPolicy.shouldPurgeOutbound(log, now, settings))
    }

    @Test
    fun `failed outbound is purged after 30 days`() {
        val log = outboundLog(
            createdAt = now.minusDays(35),
            failedAt = now.minusDays(31),
            status = "FAILED"
        )
        assertTrue(WhatsAppMediaRetentionPolicy.shouldPurgeOutbound(log, now, settings))
    }

    @Test
    fun `recent failed outbound is kept`() {
        val log = outboundLog(
            createdAt = now.minusDays(10),
            failedAt = now.minusDays(5),
            status = "FAILED"
        )
        assertFalse(WhatsAppMediaRetentionPolicy.shouldPurgeOutbound(log, now, settings))
    }

    private fun resolvedContext(
        resolvedAt: LocalDateTime = now.minusDays(200),
        hasPendingReceipt: Boolean
    ) = InboundRetentionContext(
        crmStatus = CrmConversationStatus.RESOLVED,
        resolvedAt = resolvedAt,
        hasPendingReceipt = hasPendingReceipt
    )

    private fun inboundProof(createdAt: LocalDateTime) = WhatsAppInboundMessage(
        metaMessageId = "wamid.proof.${createdAt.nano}",
        phone = "51999999999",
        messageType = "image",
        mediaMimeType = "image/jpeg",
        mediaStoredPath = "/tmp/proof.jpg",
        createdAt = createdAt
    )

    private fun inboundAudio(createdAt: LocalDateTime) = WhatsAppInboundMessage(
        metaMessageId = "wamid.audio.${createdAt.nano}",
        phone = "51999999999",
        messageType = "audio",
        mediaMimeType = "audio/ogg",
        mediaStoredPath = "/tmp/voice.ogg",
        createdAt = createdAt
    )

    private fun outboundLog(
        createdAt: LocalDateTime,
        sentAt: LocalDateTime? = createdAt,
        failedAt: LocalDateTime? = null,
        status: String
    ) = WhatsAppMessageLog(
        messageType = "OPERATOR_MEDIA",
        status = status,
        mediaStoredPath = "/tmp/out.jpg",
        createdAt = createdAt,
        sentAt = sentAt,
        failedAt = failedAt
    )
}
