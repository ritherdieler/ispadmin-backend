package com.dscorp.wispadmin.netdiag.service

object RouterOsUptimeParser {

    private val tokenRegex = Regex("(\\d+)([wdhms])")

    fun parseSeconds(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        var total = 0L
        var matched = false
        tokenRegex.findAll(raw.trim()).forEach { match ->
            matched = true
            val amount = match.groupValues[1].toLong()
            total += when (match.groupValues[2]) {
                "w" -> amount * 7 * 24 * 3600
                "d" -> amount * 24 * 3600
                "h" -> amount * 3600
                "m" -> amount * 60
                "s" -> amount
                else -> 0
            }
        }
        return if (matched) total else null
    }
}
