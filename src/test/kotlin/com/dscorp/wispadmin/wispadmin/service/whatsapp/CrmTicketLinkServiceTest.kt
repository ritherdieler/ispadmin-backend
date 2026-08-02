package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppAutoReplyProperties
import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicket
import com.dscorp.wispadmin.wispadmin.data.model.AssistanceTicketStatus
import com.dscorp.wispadmin.wispadmin.data.model.CrmChannel
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversation
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversationStatus
import com.dscorp.wispadmin.wispadmin.data.model.TicketConversationLink
import com.dscorp.wispadmin.wispadmin.repository.AssistanceTicketRepository
import com.dscorp.wispadmin.wispadmin.repository.CrmConversationRepository
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.dscorp.wispadmin.wispadmin.repository.TicketConversationLinkRepository
import com.dscorp.wispadmin.wispadmin.service.TicketNotificationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Date
import java.util.Optional

class CrmTicketLinkServiceTest {

    private val ticketRepository = mockk<AssistanceTicketRepository>()
    private val linkRepository = mockk<TicketConversationLinkRepository>()
    private val conversationRepository = mockk<CrmConversationRepository>()
    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val ticketNotificationService = mockk<TicketNotificationService>(relaxed = true)
    private val whatsAppProperties = WhatsAppProperties().apply {
        autoReply = WhatsAppAutoReplyProperties().apply { ticketDedupHours = 24 }
    }

    private lateinit var service: CrmTicketLinkService

    @BeforeEach
    fun setUp() {
        service = CrmTicketLinkService(
            ticketRepository = ticketRepository,
            linkRepository = linkRepository,
            conversationRepository = conversationRepository,
            subscriptionRepository = subscriptionRepository,
            ticketNotificationService = ticketNotificationService,
            whatsAppProperties = whatsAppProperties
        )
    }

    @Test
    fun `createTicketFromConversation reuses open duplicate within ticketDedupHours`() {
        val conversation = conversation(id = 5L, phone = "999111222")
        every { conversationRepository.findById(5L) } returns Optional.of(conversation)
        val existing = AssistanceTicket(
            id = 77,
            phone = "999111222",
            category = "Sin Conexión a Internet",
            description = "Averia previa",
            status = AssistanceTicketStatus.PENDING,
            createdAt = Date(System.currentTimeMillis() - 2 * 60 * 60 * 1000L)
        )
        every {
            ticketRepository.findOpenByPhoneAndCategorySince(eq("999111222"), eq("Sin Conexión a Internet"), any(), any())
        } returns listOf(existing)
        every { linkRepository.findByTicketId(77) } returns null
        every { linkRepository.save(any()) } answers { firstArg() }

        val result = service.createTicketFromConversation(
            conversationId = 5L,
            category = "Sin Conexión a Internet",
            description = "Nueva descripcion",
            createdBy = "agent1"
        )

        assertEquals(77, result.ticket.id)
        assertTrue(result.deduplicated)
        verify(exactly = 0) { ticketRepository.save(any()) }
        verify(exactly = 1) { linkRepository.save(any()) }
    }

    @Test
    fun `createTicketFromConversation creates ticket and unique link when no duplicate`() {
        val conversation = conversation(id = 5L, phone = "999111222", subscriptionId = 10)
        every { conversationRepository.findById(5L) } returns Optional.of(conversation)
        every {
            ticketRepository.findOpenByPhoneAndCategorySince(any(), any(), any(), any())
        } returns emptyList()
        every { subscriptionRepository.findById(10) } returns Optional.empty()
        val savedTicket = slot<AssistanceTicket>()
        every { ticketRepository.save(capture(savedTicket)) } answers {
            firstArg<AssistanceTicket>().copy(id = 88)
        }
        every { linkRepository.findByTicketId(88) } returns null
        every { linkRepository.save(any()) } answers { firstArg() }

        val result = service.createTicketFromConversation(
            conversationId = 5L,
            category = "Sin Conexión a Internet",
            description = "Fibra con luz roja",
            createdBy = "bot"
        )

        assertEquals(88, result.ticket.id)
        assertFalse(result.deduplicated)
        assertEquals("999111222", savedTicket.captured.phone)
        assertEquals("Sin Conexión a Internet", savedTicket.captured.category)
        verify { ticketNotificationService.notifyTicketCreated(any()) }
    }

    @Test
    fun `listTicketsForPhone marks high priority sla breach after 24h`() {
        val old = AssistanceTicket(
            id = 1,
            phone = "999111222",
            category = "Sin Conexión a Internet",
            description = "old",
            status = AssistanceTicketStatus.PENDING,
            priority = 10,
            createdAt = Date(System.currentTimeMillis() - 30 * 60 * 60 * 1000L)
        )
        val recent = AssistanceTicket(
            id = 2,
            phone = "999111222",
            category = "Otros",
            description = "new",
            status = AssistanceTicketStatus.ASSIGNED,
            priority = 10,
            createdAt = Date(System.currentTimeMillis() - 2 * 60 * 60 * 1000L)
        )
        every { ticketRepository.findByPhoneOrderByCreatedAtDesc("999111222") } returns listOf(old, recent)
        every { linkRepository.findByTicketId(1) } returns TicketConversationLink(
            ticketId = 1,
            conversationId = 5L,
            createdBy = "bot",
            createdAt = LocalDateTime.now()
        )
        every { linkRepository.findByTicketId(2) } returns null

        val items = service.listTicketsForPhone("999111222")

        assertEquals(2, items.size)
        assertTrue(items.first { it.id == 1 }.slaBreached)
        assertFalse(items.first { it.id == 2 }.slaBreached)
        assertEquals(5L, items.first { it.id == 1 }.conversationId)
    }

    private fun conversation(
        id: Long,
        phone: String,
        subscriptionId: Int? = null
    ) = CrmConversation(
        id = id,
        channel = CrmChannel.WHATSAPP,
        phone = phone,
        subscriptionId = subscriptionId,
        status = CrmConversationStatus.PENDING
    )
}
