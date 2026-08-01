package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppAutoReplyProperties
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class WhatsAppBusinessHoursCheckerTest {

    private val properties = WhatsAppAutoReplyProperties().apply {
        businessHours = "MON-SAT|08:00-18:00"
    }

    @Test
    fun `monday morning is within hours`() {
        val mondayMorning = LocalDateTime.of(2026, 7, 27, 9, 0) // Monday
        assertTrue(WhatsAppBusinessHoursChecker.isWithinBusinessHours(properties, mondayMorning))
    }

    @Test
    fun `sunday is outside hours`() {
        val sunday = LocalDateTime.of(2026, 7, 26, 10, 0) // Sunday
        assertFalse(WhatsAppBusinessHoursChecker.isWithinBusinessHours(properties, sunday))
    }

    @Test
    fun `saturday evening is outside hours`() {
        val saturdayNight = LocalDateTime.of(2026, 7, 25, 20, 0) // Saturday
        assertFalse(WhatsAppBusinessHoursChecker.isWithinBusinessHours(properties, saturdayNight))
    }
}
