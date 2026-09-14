package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.CrmEventLog
import com.dscorp.wispadmin.wispadmin.dto.CrmRealtimeEventDto
import com.dscorp.wispadmin.wispadmin.repository.CrmEventLogRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.time.LocalDateTime

class CrmEventPublisherTest {

    private val repository = mockk<CrmEventLogRepository>()
    private val messagingTemplate = mockk<SimpMessagingTemplate>(relaxed = true)
    private val objectMapper = ObjectMapper()

    private lateinit var publisher: CrmEventPublisher

    @BeforeEach
    fun setUp() {
        publisher = CrmEventPublisher(repository, messagingTemplate, objectMapper)
    }

    @Test
    fun `publish persists event and sends to topic whatsapp`() {
        val savedSlot = slot<CrmEventLog>()
        every { repository.save(capture(savedSlot)) } answers {
            firstArg<CrmEventLog>().copy(id = 42L)
        }

        val dto = publisher.publish(
            eventType = CrmEventPublisher.MESSAGE_RECEIVED,
            payload = mapOf(
                "phone" to "51902354183",
                "inboundMessageId" to 7
            )
        )

        assertEquals(42L, dto.eventId)
        assertEquals(CrmEventPublisher.MESSAGE_RECEIVED, dto.eventType)
        assertEquals("51902354183", dto.payload["phone"])
        assertTrue(savedSlot.captured.payload.contains("51902354183"))
        verify {
            messagingTemplate.convertAndSend("/topic/whatsapp", match<CrmRealtimeEventDto> {
                it.eventId == 42L && it.eventType == CrmEventPublisher.MESSAGE_RECEIVED
            })
        }
    }

    @Test
    fun `findSince returns events after given id ordered ascending`() {
        val createdAt = LocalDateTime.of(2026, 8, 2, 10, 0)
        every { repository.findByIdGreaterThanOrderByIdAsc(10L) } returns listOf(
            CrmEventLog(
                id = 11L,
                eventType = CrmEventPublisher.MESSAGE_STATUS,
                payload = """{"phone":"51902354183","deliveryStatus":"delivered"}""",
                createdAt = createdAt
            ),
            CrmEventLog(
                id = 12L,
                eventType = CrmEventPublisher.CONVERSATION_UPDATED,
                payload = """{"phone":"51902354183","unreadCount":2}""",
                createdAt = createdAt.plusSeconds(1)
            )
        )

        val events = publisher.findSince(10L)

        assertEquals(2, events.size)
        assertEquals(11L, events[0].eventId)
        assertEquals(CrmEventPublisher.MESSAGE_STATUS, events[0].eventType)
        assertEquals("delivered", events[0].payload["deliveryStatus"])
        assertEquals(12L, events[1].eventId)
        assertEquals(2, events[1].payload["unreadCount"])
    }
}
