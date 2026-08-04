package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.data.model.WhatsAppMessageLog
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppAccountEventRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppMessageLogRepository
import com.dscorp.wispadmin.wispadmin.repository.WhatsAppSyncedTemplateRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class WhatsAppAccountEventServiceTest {

    private val accountEventRepository = mockk<WhatsAppAccountEventRepository>()
    private val messageLogRepository = mockk<WhatsAppMessageLogRepository>()
    private val syncedTemplateRepository = mockk<WhatsAppSyncedTemplateRepository>()
    private val metaAnalyticsClient = mockk<WhatsAppMetaAnalyticsClient>()
    private val objectMapper = ObjectMapper()

    private val service = WhatsAppAccountEventService(
        accountEventRepository,
        messageLogRepository,
        syncedTemplateRepository,
        metaAnalyticsClient
    )

    private fun stubBaseline(phoneHealthJson: String, sentLogsCount: Int, totalLogsCount: Int = sentLogsCount) {
        every { accountEventRepository.findTop20ByOrderByCreatedAtDesc() } returns emptyList()
        every { syncedTemplateRepository.findAllByOrderByNameAsc() } returns emptyList()
        every { metaAnalyticsClient.fetchPhoneNumberHealth() } returns objectMapper.readTree(phoneHealthJson)
        val sentLogs = (1..sentLogsCount).map {
            WhatsAppMessageLog(status = WhatsAppTemplateDeliveryService.STATUS_SENT, createdAt = LocalDateTime.now())
        }
        val otherLogs = (1..(totalLogsCount - sentLogsCount)).map {
            WhatsAppMessageLog(status = WhatsAppTemplateDeliveryService.STATUS_FAILED, createdAt = LocalDateTime.now())
        }
        every { messageLogRepository.findByCreatedAtBetween(any(), any()) } returns sentLogs + otherLogs
    }

    @Test
    fun `getAccountHealth exposes real messagingLimitTier and numeric limit for TIER_250`() {
        stubBaseline("""{"messaging_limit_tier":"TIER_250","quality_rating":"GREEN"}""", sentLogsCount = 5)

        val health = service.getAccountHealth()

        assertEquals("TIER_250", health.messagingLimitTier)
        assertEquals(250, health.messagingLimit)
    }

    @Test
    fun `getAccountHealth maps TIER_2K to numeric limit 2000 once business verification is completed`() {
        stubBaseline("""{"messaging_limit_tier":"TIER_2K","quality_rating":"GREEN"}""", sentLogsCount = 5)

        val health = service.getAccountHealth()

        assertEquals("TIER_2K", health.messagingLimitTier)
        assertEquals(2000, health.messagingLimit)
    }

    @Test
    fun `getAccountHealth returns null numeric limit for unknown tier`() {
        stubBaseline("""{"messaging_limit_tier":"TIER_UNKNOWN","quality_rating":"GREEN"}""", sentLogsCount = 1)

        val health = service.getAccountHealth()

        assertNull(health.messagingLimit)
    }

    @Test
    fun `getAccountHealth exposes messagingUsedToday counting SENT logs in the last 24h`() {
        stubBaseline(
            """{"messaging_limit_tier":"TIER_250","quality_rating":"GREEN"}""",
            sentLogsCount = 184,
            totalLogsCount = 200
        )

        val health = service.getAccountHealth()

        assertEquals(184, health.messagingUsedToday)
    }

    @Test
    fun `getAccountHealth adds warning alert when usage is near the daily limit`() {
        stubBaseline("""{"messaging_limit_tier":"TIER_250","quality_rating":"GREEN"}""", sentLogsCount = 220)

        val health = service.getAccountHealth()

        val alert = health.alerts.firstOrNull { it.type == "MESSAGING_LIMIT_NEAR" }
        assertTrue(alert != null)
        assertEquals("WARNING", alert?.severity)
    }

    @Test
    fun `getAccountHealth adds critical alert when usage reaches the daily limit`() {
        stubBaseline("""{"messaging_limit_tier":"TIER_250","quality_rating":"GREEN"}""", sentLogsCount = 250)

        val health = service.getAccountHealth()

        val alert = health.alerts.firstOrNull { it.type == "MESSAGING_LIMIT_REACHED" }
        assertTrue(alert != null)
        assertEquals("CRITICAL", alert?.severity)
    }

    @Test
    fun `getAccountHealth does not add limit alerts when usage is comfortably below the limit`() {
        stubBaseline("""{"messaging_limit_tier":"TIER_250","quality_rating":"GREEN"}""", sentLogsCount = 50)

        val health = service.getAccountHealth()

        assertTrue(health.alerts.none { it.type == "MESSAGING_LIMIT_NEAR" || it.type == "MESSAGING_LIMIT_REACHED" })
    }
}
