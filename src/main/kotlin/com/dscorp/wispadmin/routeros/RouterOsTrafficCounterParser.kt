package com.dscorp.wispadmin.routeros

object RouterOsTrafficCounterParser {

    fun parseUpDown(raw: String?): Pair<Long, Long>? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val parts = value.split("/")
        return when (parts.size) {
            1 -> {
                val single = parts[0].trim().toLongOrNull() ?: return null
                single to single
            }
            else -> {
                val up = parts[0].trim().toLongOrNull() ?: return null
                val down = parts[1].trim().toLongOrNull() ?: return null
                up to down
            }
        }
    }

    fun normalizeTarget(target: String?): String? {
        val value = target?.trim().orEmpty()
        if (value.isEmpty()) return null
        return value.substringBefore("/").trim().ifEmpty { null }
    }

    fun computeDelta(previous: Long?, current: Long?, counterReset: Boolean): Long {
        if (counterReset || previous == null || current == null) return 0L
        val delta = current - previous
        return if (delta > 0L) delta else 0L
    }

    fun bytesToMbps(bytesDelta: Long, intervalSeconds: Double): Double {
        if (bytesDelta <= 0L || intervalSeconds <= 0.0) return 0.0
        return (bytesDelta * 8.0) / (1_000_000.0 * intervalSeconds)
    }
}
