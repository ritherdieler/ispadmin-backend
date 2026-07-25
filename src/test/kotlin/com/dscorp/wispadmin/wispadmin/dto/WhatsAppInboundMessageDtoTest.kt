package com.dscorp.wispadmin.wispadmin.dto

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class WhatsAppInboundMessageDtoTest {

    @Test
    fun `toDto exposes persisted readAt`() {
        val readAt = LocalDateTime.of(2026, 7, 25, 11, 30)
        val entity = WhatsAppInboundMessage(
            id = 3,
            metaMessageId = "wamid.x",
            phone = "51902354183",
            messageText = "Hola",
            readAt = readAt,
            createdAt = LocalDateTime.of(2026, 7, 25, 10, 0)
        )

        val dto = entity.toDto(clientName = "Ana Lopez")

        assertEquals(readAt, dto.readAt)
        assertEquals("Ana Lopez", dto.clientName)
    }
}
