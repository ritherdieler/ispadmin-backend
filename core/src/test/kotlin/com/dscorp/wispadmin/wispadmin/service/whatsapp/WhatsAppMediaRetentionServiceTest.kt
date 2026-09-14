package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.dscorp.wispadmin.wispadmin.config.WhatsAppRetentionProperties
import com.dscorp.wispadmin.wispadmin.data.model.CrmChannel
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversation
import com.dscorp.wispadmin.wispadmin.data.model.CrmConversationStatus
import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppInboundMessage
import com.dscorp.wispadmin.wispadmin.repository.CrmConversationRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppInboundMessageRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import java.nio.file.Files
import java.time.LocalDateTime

class WhatsAppMediaRetentionServiceTest {

    private val whatsAppProperties = WhatsAppProperties().apply {
        retention = WhatsAppRetentionProperties()
    }
    private val inboundMessageRepository = mockk<WhatsAppInboundMessageRepository>()
    private val messageLogRepository = mockk<WhatsAppMessageLogRepository>()
    private val crmConversationRepository = mockk<CrmConversationRepository>()
    private val mediaDownloadService = mockk<WhatsAppMediaDownloadService>()

    private lateinit var service: WhatsAppMediaRetentionService

    private val now = LocalDateTime.of(2026, 8, 10, 12, 0)

    @BeforeEach
    fun setUp() {
        service = WhatsAppMediaRetentionService(
            whatsAppProperties = whatsAppProperties,
            inboundMessageRepository = inboundMessageRepository,
            messageLogRepository = messageLogRepository,
            crmConversationRepository = crmConversationRepository,
            mediaDownloadService = mediaDownloadService,
        )
        every { messageLogRepository.findOutboundMediaRetentionCandidates(any(), any()) } returns emptyList()
        every { mediaDownloadService.resolveStoredPath(any()) } answers {
            val stored = firstArg<String?>()
            if (stored.isNullOrBlank()) null else java.nio.file.Paths.get(stored)
        }
    }

    @Test
    fun `purges expired payment proof and clears stored path`() {
        val tempFile = Files.createTempFile("wa-retention-", ".jpg")
        val inbound = WhatsAppInboundMessage(
            id = 1,
            metaMessageId = "wamid.old-proof",
            phone = "51911111111",
            messageType = "image",
            mediaMimeType = "image/jpeg",
            mediaStoredPath = tempFile.toAbsolutePath().toString(),
            createdAt = now.minusDays(800),
        )
        val crm = CrmConversation(
            phone = "51911111111",
            channel = CrmChannel.WHATSAPP,
            status = CrmConversationStatus.RESOLVED,
            resolvedAt = now.minusDays(400),
        )
        every {
            inboundMessageRepository.findInboundMediaRetentionCandidates(any(), any())
        } returnsMany listOf(listOf(inbound), emptyList())
        every {
            crmConversationRepository.findByChannelAndPhoneIn(CrmChannel.WHATSAPP, any())
        } returns listOf(crm)
        every {
            inboundMessageRepository.findLatestMediaAtByPhoneIn(any())
        } returns listOf(arrayOf<Any>("51911111111", java.sql.Timestamp.valueOf(now.minusDays(800))))
        val saved = slot<WhatsAppInboundMessage>()
        every { inboundMessageRepository.save(capture(saved)) } answers { firstArg() }

        val result = service.purgeExpiredMedia(now)

        assertEquals(1, result.inboundPurged)
        assertNull(saved.captured.mediaStoredPath)
        assertNotNull(saved.captured.mediaPurgedAt)
        verify(exactly = 1) { inboundMessageRepository.save(any()) }
    }

    @Test
    fun `skips payment proof while pending receipt`() {
        val inbound = WhatsAppInboundMessage(
            id = 2,
            metaMessageId = "wamid.pending",
            phone = "51922222222",
            messageType = "image",
            mediaStoredPath = "/tmp/keep.jpg",
            createdAt = now.minusDays(800),
        )
        val crm = CrmConversation(
            phone = "51922222222",
            channel = CrmChannel.WHATSAPP,
            status = CrmConversationStatus.RESOLVED,
            resolvedAt = now.minusDays(10),
        )
        every {
            inboundMessageRepository.findInboundMediaRetentionCandidates(any(), any())
        } returnsMany listOf(listOf(inbound), emptyList())
        every {
            crmConversationRepository.findByChannelAndPhoneIn(CrmChannel.WHATSAPP, any())
        } returns listOf(crm)
        every {
            inboundMessageRepository.findLatestMediaAtByPhoneIn(any())
        } returns listOf(arrayOf<Any>("51922222222", java.sql.Timestamp.valueOf(now.minusDays(1))))

        val result = service.purgeExpiredMedia(now)

        assertEquals(0, result.inboundPurged)
        verify(exactly = 0) { inboundMessageRepository.save(any()) }
    }
}
