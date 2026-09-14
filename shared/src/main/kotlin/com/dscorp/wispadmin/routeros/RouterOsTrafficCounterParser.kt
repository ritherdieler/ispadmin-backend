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
        val first = value.split(",").first().trim()
        val ip = first.substringBefore("/").trim().ifEmpty { null } ?: return null
        return ip.takeIf { it.length <= 45 }
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

    fun parseMaxLimitMbps(raw: String?): Pair<Int, Int>? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value == "0/0") return null
        val parts = value.split("/")
        if (parts.size < 2) return null
        val up = parseLimitToMbps(parts[0]) ?: return null
        val down = parseLimitToMbps(parts[1]) ?: return null
        return up to down
    }

    private fun parseLimitToMbps(raw: String): Int? {
        val t = raw.trim().uppercase()
        if (t.isEmpty()) return null
        return when {
            t.endsWith("G") -> t.dropLast(1).toDoubleOrNull()?.times(1000)?.toInt()
            t.endsWith("M") -> t.dropLast(1).toDoubleOrNull()?.toInt()
            t.endsWith("K") -> t.dropLast(1).toDoubleOrNull()?.div(1000)?.toInt()
            else -> t.toLongOrNull()?.let { (it / 1_000_000L).toInt() }
        }
    }
}
