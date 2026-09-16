package com.dscorp.wispadmin.acs.genieacs

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

object GenieAcsValues {
    fun cidrToSubnetMask(cidr: String): String {
        val prefix = cidr.substringAfter("/", "24").toIntOrNull()?.coerceIn(0, 32) ?: 24
        val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
        return listOf(24, 16, 8, 0).joinToString(".") { shift ->
            ((mask ushr shift) and 0xff).toString()
        }
    }

    fun parseDateTime(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        return try {
            Instant.parse(raw).atZone(ZoneOffset.UTC).toLocalDateTime()
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(raw)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
