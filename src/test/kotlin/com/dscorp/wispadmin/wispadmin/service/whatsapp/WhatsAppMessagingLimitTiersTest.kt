package com.dscorp.wispadmin.wispadmin.service.whatsapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WhatsAppMessagingLimitTiersTest {

    @Test
    fun `dailyLimitFor maps known tiers`() {
        assertEquals(250, WhatsAppMessagingLimitTiers.dailyLimitFor("TIER_250"))
        assertEquals(2000, WhatsAppMessagingLimitTiers.dailyLimitFor("TIER_2K"))
    }

    @Test
    fun `resolveDailyLimit prefers positive override`() {
        assertEquals(2000, WhatsAppMessagingLimitTiers.resolveDailyLimit("TIER_250", 2000))
        assertEquals(250, WhatsAppMessagingLimitTiers.resolveDailyLimit("TIER_250", 0))
        assertEquals(250, WhatsAppMessagingLimitTiers.resolveDailyLimit("TIER_250", null))
        assertNull(WhatsAppMessagingLimitTiers.resolveDailyLimit("TIER_UNKNOWN", null))
    }
}
