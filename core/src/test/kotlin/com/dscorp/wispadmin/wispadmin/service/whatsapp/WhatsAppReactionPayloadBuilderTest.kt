package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WhatsAppReactionPayloadBuilderTest {

    private val mapper = ObjectMapper()

    @Test
    fun `reaction payload includes message_id and emoji`() {
        val body = WhatsAppReactionPayloadBuilder.build(
            phoneNumber = "987654321",
            wamid = "wamid.ABC123",
            emoji = "👍",
        )
        val json = mapper.writeValueAsString(body)
        assertTrue(json.contains("\"type\":\"reaction\""))
        assertTrue(json.contains("\"message_id\":\"wamid.ABC123\""))
        assertTrue(json.contains("\"emoji\":\"👍\"") || json.contains("\\uD83D\\uDC4D"))
        assertEquals("51987654321", body.to)
        assertEquals("wamid.ABC123", body.reaction.message_id)
        assertEquals("👍", body.reaction.emoji)
    }

    @Test
    fun `empty emoji is allowed to remove reaction`() {
        val body = WhatsAppReactionPayloadBuilder.build(
            phoneNumber = "51987654321",
            wamid = "wamid.REMOVE",
            emoji = "",
        )
        assertEquals("", body.reaction.emoji)
        assertEquals("wamid.REMOVE", body.reaction.message_id)
    }
}

class WhatsAppMessageMutationPolicyTest {

    @Test
    fun `edit allowed for outbound plain text within 15 minutes`() {
        val created = java.time.LocalDateTime.parse("2026-08-06T12:00:00")
        val now = created.plusMinutes(10).atZone(java.time.ZoneId.systemDefault()).toInstant()
        assertTrue(
            WhatsAppMessageMutationPolicy.canEditOutbound(
                direction = "OUTBOUND",
                messageType = "OPERATOR_REPLY",
                createdAt = created,
                now = now,
            ),
        )
    }

    @Test
    fun `edit denied after 15 minutes or for inbound`() {
        val created = java.time.LocalDateTime.parse("2026-08-06T12:00:00")
        val now = created.plusMinutes(16).atZone(java.time.ZoneId.systemDefault()).toInstant()
        assertFalse(
            WhatsAppMessageMutationPolicy.canEditOutbound(
                direction = "OUTBOUND",
                messageType = "OPERATOR_REPLY",
                createdAt = created,
                now = now,
            ),
        )
        assertFalse(
            WhatsAppMessageMutationPolicy.canEditOutbound(
                direction = "INBOUND",
                messageType = "text",
                createdAt = created,
                now = created.plusMinutes(1).atZone(java.time.ZoneId.systemDefault()).toInstant(),
            ),
        )
    }

    @Test
    fun `delete allowed within 24 hours for outbound`() {
        val created = java.time.LocalDateTime.parse("2026-08-06T12:00:00")
        val now = created.plusHours(23).atZone(java.time.ZoneId.systemDefault()).toInstant()
        assertTrue(
            WhatsAppMessageMutationPolicy.canDeleteOutbound(
                direction = "OUTBOUND",
                createdAt = created,
                now = now,
            ),
        )
        assertFalse(
            WhatsAppMessageMutationPolicy.canDeleteOutbound(
                direction = "OUTBOUND",
                createdAt = created,
                now = created.plusHours(25).atZone(java.time.ZoneId.systemDefault()).toInstant(),
            ),
        )
    }
}
