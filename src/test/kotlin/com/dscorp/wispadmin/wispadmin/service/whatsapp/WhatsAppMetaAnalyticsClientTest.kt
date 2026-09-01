package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class WhatsAppMetaAnalyticsClientTest {

    private val whatsAppProperties = WhatsAppProperties().apply {
        apiVersion = "v21.0"
        accessToken = "token"
        phoneNumberId = "123"
        businessAccountId = "456"
    }
    private val client = WhatsAppMetaAnalyticsClient(whatsAppProperties)
    private val objectMapper = ObjectMapper()

    @Test
    fun `parseMessagingLimitTier reads messaging_limit_tier field`() {
        val node = objectMapper.readTree("""{"messaging_limit_tier":"TIER_250","quality_rating":"GREEN"}""")
        assertEquals("TIER_250", WhatsAppMetaAnalyticsClient.parseMessagingLimitTier(node))
    }

    @Test
    fun `parseQualityRating reads quality_rating field`() {
        val node = objectMapper.readTree("""{"messaging_limit_tier":"TIER_250","quality_rating":"GREEN"}""")
        assertEquals("GREEN", WhatsAppMetaAnalyticsClient.parseQualityRating(node))
    }

    @Test
    fun `parse functions return null when fields are absent`() {
        val node = objectMapper.readTree("""{}""")
        assertNull(WhatsAppMetaAnalyticsClient.parseMessagingLimitTier(node))
        assertNull(WhatsAppMetaAnalyticsClient.parseQualityRating(node))
    }

    @Test
    fun `fetchWithCache reuses cached value within ttl window`() {
        var calls = 0
        val now = Instant.parse("2026-08-04T10:00:00Z")
        val fetcher = { calls++; objectMapper.readTree("""{"messaging_limit_tier":"TIER_250"}""") }

        client.fetchWithCache(now, fetcher)
        client.fetchWithCache(now.plus(Duration.ofMinutes(4)), fetcher)

        assertEquals(1, calls)
    }

    @Test
    fun `fetchWithCache refetches after ttl expires`() {
        var calls = 0
        val now = Instant.parse("2026-08-04T10:00:00Z")
        val fetcher = { calls++; objectMapper.readTree("""{"messaging_limit_tier":"TIER_250"}""") }

        client.fetchWithCache(now, fetcher)
        client.fetchWithCache(now.plus(Duration.ofMinutes(6)), fetcher)

        assertEquals(2, calls)
    }
}
