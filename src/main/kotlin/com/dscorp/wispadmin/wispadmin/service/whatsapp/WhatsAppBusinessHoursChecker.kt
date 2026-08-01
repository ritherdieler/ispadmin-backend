package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppAutoReplyProperties
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Parses business-hours config like: MON-SAT|08:00-18:00
 */
object WhatsAppBusinessHoursChecker {

    private val zone = ZoneId.of("America/Lima")

    fun isWithinBusinessHours(
        properties: WhatsAppAutoReplyProperties,
        now: LocalDateTime = LocalDateTime.now(zone)
    ): Boolean {
        val raw = properties.businessHours.trim()
        if (raw.isBlank()) return true

        val parts = raw.split("|")
        if (parts.size != 2) return true

        val days = parseDays(parts[0].trim())
        val times = parts[1].trim().split("-")
        if (times.size != 2 || days.isEmpty()) return true

        val start = LocalTime.parse(times[0].trim())
        val end = LocalTime.parse(times[1].trim())
        val day = now.dayOfWeek
        val time = now.toLocalTime()

        return day in days && !time.isBefore(start) && time.isBefore(end)
    }

    private fun parseDays(range: String): Set<DayOfWeek> {
        val map = mapOf(
            "MON" to DayOfWeek.MONDAY,
            "TUE" to DayOfWeek.TUESDAY,
            "WED" to DayOfWeek.WEDNESDAY,
            "THU" to DayOfWeek.THURSDAY,
            "FRI" to DayOfWeek.FRIDAY,
            "SAT" to DayOfWeek.SATURDAY,
            "SUN" to DayOfWeek.SUNDAY
        )
        val tokens = range.split("-").map { it.trim().uppercase() }
        if (tokens.size == 1) {
            return setOfNotNull(map[tokens[0]])
        }
        if (tokens.size != 2) return emptySet()
        val start = map[tokens[0]] ?: return emptySet()
        val end = map[tokens[1]] ?: return emptySet()
        val ordered = DayOfWeek.values().toList()
        val startIdx = ordered.indexOf(start)
        val endIdx = ordered.indexOf(end)
        if (startIdx < 0 || endIdx < 0) return emptySet()
        return if (startIdx <= endIdx) {
            ordered.subList(startIdx, endIdx + 1).toSet()
        } else {
            (ordered.subList(startIdx, ordered.size) + ordered.subList(0, endIdx + 1)).toSet()
        }
    }
}
