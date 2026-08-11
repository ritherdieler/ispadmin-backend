package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppAutoReplyProperties
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class WhatsAppBusinessHoursCheckerTest {

    private val properties = WhatsAppAutoReplyProperties().apply {
        businessHours = "MON-FRI|08:00-17:30;SAT|08:00-12:30"
    }

    @Test
    fun `monday morning is within hours`() {
        val mondayMorning = LocalDateTime.of(2026, 7, 27, 9, 0)
        assertTrue(WhatsAppBusinessHoursChecker.isWithinBusinessHours(properties, mondayMorning))
    }

    @Test
    fun `friday late afternoon within weekday window`() {
        val fridayAfternoon = LocalDateTime.of(2026, 7, 31, 17, 0)
        assertTrue(WhatsAppBusinessHoursChecker.isWithinBusinessHours(properties, fridayAfternoon))
    }

    @Test
    fun `friday after weekday close is outside hours`() {
        val fridayEvening = LocalDateTime.of(2026, 7, 31, 17, 30)
        assertFalse(WhatsAppBusinessHoursChecker.isWithinBusinessHours(properties, fridayEvening))
    }

    @Test
    fun `saturday morning is within hours`() {
        val saturdayMorning = LocalDateTime.of(2026, 7, 25, 10, 0)
        assertTrue(WhatsAppBusinessHoursChecker.isWithinBusinessHours(properties, saturdayMorning))
    }

    @Test
    fun `saturday afternoon after saturday close is outside hours`() {
        val saturdayAfternoon = LocalDateTime.of(2026, 7, 25, 13, 0)
        assertFalse(WhatsAppBusinessHoursChecker.isWithinBusinessHours(properties, saturdayAfternoon))
    }

    @Test
    fun `sunday is outside hours`() {
        val sunday = LocalDateTime.of(2026, 7, 26, 10, 0)
        assertFalse(WhatsAppBusinessHoursChecker.isWithinBusinessHours(properties, sunday))
    }

    @Test
    fun `legacy single range format still works`() {
        val legacy = WhatsAppAutoReplyProperties().apply {
            businessHours = "MON-SAT|08:00-18:00"
        }
        val saturdayNight = LocalDateTime.of(2026, 7, 25, 20, 0)
        assertFalse(WhatsAppBusinessHoursChecker.isWithinBusinessHours(legacy, saturdayNight))
    }
}
