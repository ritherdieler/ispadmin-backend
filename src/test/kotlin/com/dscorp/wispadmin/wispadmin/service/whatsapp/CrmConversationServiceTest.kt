package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.CrmAssignmentEvent
import com.dscorp.wispadmin.wispadmin.data.model.CrmAssignmentEventType
import com.dscorp.wispadmin.wispadmin.data.model.CrmChannel
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversation
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversationStatus
import com.dscorp.wispadmin.wispadmin.data.model.CrmInternalNote
import com.dscorp.wispadmin.wispadmin.data.model.User
import com.dscorp.wispadmin.wispadmin.repository.CrmAssignmentEventRepository
import com.dscorp.wispadmin.wispadmin.repository.CrmConversationRepository
import com.dscorp.wispadmin.wispadmin.repository.CrmInternalNoteRepository
import com.dscorp.wispadmin.wispadmin.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CrmConversationServiceTest {

    private val conversationRepository = mockk<CrmConversationRepository>()
    private val assignmentEventRepository = mockk<CrmAssignmentEventRepository>()
    private val internalNoteRepository = mockk<CrmInternalNoteRepository>()
    private val userRepository = mockk<UserRepository>()
    private val crmEventPublisher = mockk<CrmEventPublisher>(relaxed = true)
    private val handoffService = mockk<WhatsAppHandoffService>(relaxed = true)
    private val chatStateService = mockk<WhatsAppChatStateService>(relaxed = true)
    private val llmClient = mockk<LlmClient>(relaxed = true)
    private val inboundMessageRepository = mockk<com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository>(relaxed = true)
    private val messageLogRepository = mockk<com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository>(relaxed = true)

    private lateinit var service: CrmConversationService

    @BeforeEach
    fun setUp() {
        service = CrmConversationService(
            conversationRepository = conversationRepository,
            assignmentEventRepository = assignmentEventRepository,
            internalNoteRepository = internalNoteRepository,
            userRepository = userRepository,
            crmEventPublisher = crmEventPublisher,
            handoffService = handoffService,
            chatStateService = chatStateService,
            llmClient = llmClient,
            inboundMessageRepository = inboundMessageRepository,
            messageLogRepository = messageLogRepository
        )
        every { assignmentEventRepository.save(any()) } answers { firstArg() }
        every { internalNoteRepository.save(any()) } answers { firstArg() }
        every { chatStateService.isBotPaused(any()) } returns false
        every { userRepository.findById(any()) } answers {
            Optional.of(
                User(
                    id = firstArg(),
                    name = "Agent",
                    lastName = "One",
                    username = "agent${firstArg<Int>()}"
                )
            )
        }
    }

    @Test
    fun `claim succeeds when conversation is unassigned`() {
        val conversation = pendingConversation(id = 10L)
        every { conversationRepository.findById(10L) } returns Optional.of(conversation)
        every {
            conversationRepository.claimIfUnassigned(10L, 7, any())
        } returns 1
        every { conversationRepository.findById(10L) } returnsMany listOf(
            Optional.of(conversation),
            Optional.of(
                conversation.copy(
                    status = CrmConversationStatus.ASSIGNED,
                    assignedAgentId = 7,
                    claimedAt = LocalDateTime.now()
                )
            )
        )

        val result = service.claim(10L, agentId = 7, operatorUsername = "agent7")

        assertEquals("ASSIGNED", result.status)
        assertEquals(7, result.assignedAgentId)
        verify {
            assignmentEventRepository.save(match {
                it.eventType == CrmAssignmentEventType.CLAIM && it.toUserId == 7
            })
        }
        verify {
            crmEventPublisher.publish(
                CrmEventPublisher.CONVERSATION_UPDATED,
                match { it["status"] == "ASSIGNED" && it["assignedAgentId"] == 7 }
            )
        }
    }

    @Test
    fun `claim fails when already assigned`() {
        val conversation = pendingConversation(id = 11L).copy(
            status = CrmConversationStatus.ASSIGNED,
            assignedAgentId = 3
        )
        every { conversationRepository.findById(11L) } returns Optional.of(conversation)
        every { conversationRepository.claimIfUnassigned(11L, 7, any()) } returns 0

        assertThrows(CrmConversationConflictException::class.java) {
            service.claim(11L, agentId = 7, operatorUsername = "agent7")
        }
    }

    @Test
    fun `concurrent claim allows only one winner`() {
        val conversation = pendingConversation(id = 12L)
        val claimed = AtomicInteger(0)
        every { conversationRepository.findById(12L) } answers {
            if (claimed.get() == 0) {
                Optional.of(conversation)
            } else {
                Optional.of(
                    conversation.copy(
                        status = CrmConversationStatus.ASSIGNED,
                        assignedAgentId = 1,
                        claimedAt = LocalDateTime.now()
                    )
                )
            }
        }
        every { conversationRepository.claimIfUnassigned(12L, any(), any()) } answers {
            if (claimed.compareAndSet(0, secondArg())) 1 else 0
        }

        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val successes = AtomicInteger(0)
        val conflicts = AtomicInteger(0)

        listOf(1, 2).forEach { agentId ->
            pool.submit {
                start.await()
                try {
                    service.claim(12L, agentId = agentId, operatorUsername = "agent$agentId")
                    successes.incrementAndGet()
                } catch (_: CrmConversationConflictException) {
                    conflicts.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }

        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        pool.shutdownNow()

        assertEquals(1, successes.get())
        assertEquals(1, conflicts.get())
    }

    @Test
    fun `release clears assignee and sets PENDING`() {
        val conversation = pendingConversation(id = 13L).copy(
            status = CrmConversationStatus.ASSIGNED,
            assignedAgentId = 7,
            claimedAt = LocalDateTime.now()
        )
        every { conversationRepository.findById(13L) } returns Optional.of(conversation)
        every { conversationRepository.save(any()) } answers { firstArg() }

        val result = service.release(13L, agentId = 7, operatorUsername = "agent7", isAdmin = false)

        assertEquals("PENDING", result.status)
        assertNull(result.assignedAgentId)
        verify {
            assignmentEventRepository.save(match { it.eventType == CrmAssignmentEventType.RELEASE })
        }
    }

    @Test
    fun `transfer requires note and moves assignee`() {
        val conversation = pendingConversation(id = 14L).copy(
            status = CrmConversationStatus.ASSIGNED,
            assignedAgentId = 7
        )
        every { conversationRepository.findById(14L) } returns Optional.of(conversation)
        every { conversationRepository.save(any()) } answers { firstArg() }

        val result = service.transfer(
            conversationId = 14L,
            fromAgentId = 7,
            toAgentId = 9,
            note = "Turno noche",
            operatorUsername = "agent7",
            isAdmin = false
        )

        assertEquals(9, result.assignedAgentId)
        assertEquals("ASSIGNED", result.status)
        verify {
            assignmentEventRepository.save(match {
                it.eventType == CrmAssignmentEventType.TRANSFER &&
                    it.fromUserId == 7 &&
                    it.toUserId == 9 &&
                    it.note == "Turno noche"
            })
        }
    }

    @Test
    fun `resolve resumes bot and clears assignee`() {
        val conversation = pendingConversation(id = 15L).copy(
            status = CrmConversationStatus.ASSIGNED,
            assignedAgentId = 7,
            phone = "51999999999"
        )
        every { conversationRepository.findById(15L) } returns Optional.of(conversation)
        every { conversationRepository.save(any()) } answers { firstArg() }

        val result = service.resolve(
            conversationId = 15L,
            agentId = 7,
            operatorUsername = "agent7",
            isAdmin = false,
            resumeBot = true,
            note = null
        )

        assertEquals("RESOLVED", result.status)
        assertNull(result.assignedAgentId)
        verify { handoffService.resumeBotAndTakeControl("51999999999", "crm_resolved") }
    }

    @Test
    fun `assertCanReply allows assignee and admin only`() {
        val conversation = pendingConversation(id = 16L).copy(
            status = CrmConversationStatus.ASSIGNED,
            assignedAgentId = 7,
            phone = "51911111111"
        )
        every { conversationRepository.findByPhoneAndChannel("51911111111", CrmChannel.WHATSAPP) } returns conversation

        service.assertCanReply(phone = "51911111111", agentId = 7, isAdmin = false)
        service.assertCanReply(phone = "51911111111", agentId = 99, isAdmin = true)

        assertThrows(CrmConversationForbiddenException::class.java) {
            service.assertCanReply(phone = "51911111111", agentId = 99, isAdmin = false)
        }
    }

    @Test
    fun `markPendingOnHandoff creates or updates conversation`() {
        every {
            conversationRepository.findByPhoneAndChannel("51922222222", CrmChannel.WHATSAPP)
        } returns null
        every {
            conversationRepository.findByPhoneAndChannel("922222222", CrmChannel.WHATSAPP)
        } returns null
        every { conversationRepository.save(any()) } answers {
            firstArg<CrmConversation>().copy(id = 20L)
        }

        val created = service.markPendingOnHandoff(
            phone = "51922222222",
            subscriptionId = 44,
            reason = "human_escalation"
        )

        assertEquals(CrmConversationStatus.PENDING, created.status)
        assertEquals(44, created.subscriptionId)
        assertEquals("51922222222", created.phone)
        verify {
            crmEventPublisher.publish(
                CrmEventPublisher.CONVERSATION_UPDATED,
                match { it["status"] == "PENDING" && it["phone"] == "51922222222" }
            )
        }
    }

    @Test
    fun `addInternalNote persists note`() {
        every { conversationRepository.findById(21L) } returns Optional.of(pendingConversation(21L))
        every { internalNoteRepository.save(any()) } answers {
            firstArg<CrmInternalNote>().copy(id = 5L)
        }

        val note = service.addInternalNote(
            conversationId = 21L,
            authorId = 7,
            text = "Cliente molesto",
            operatorUsername = "agent7"
        )

        assertEquals("Cliente molesto", note.text)
        assertEquals(7, note.authorId)
    }

    private fun pendingConversation(id: Long) = CrmConversation(
        id = id,
        channel = CrmChannel.WHATSAPP,
        phone = "51900000000",
        status = CrmConversationStatus.PENDING,
        priority = 0,
        lastInboundAt = LocalDateTime.now()
    )
}
