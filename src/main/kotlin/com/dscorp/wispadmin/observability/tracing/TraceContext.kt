package com.dscorp.wispadmin.observability.tracing

import java.util.concurrent.ThreadLocalRandom

data class TraceScope(
    val traceId: String,
    var currentSpanId: String,
    val sessionId: String?,
    val platform: String,
    val environment: String?,
    val release: String?,
    val sampled: Boolean = true
)

object TraceContext {

    private val holder = ThreadLocal<TraceScope?>()

    fun current(): TraceScope? = holder.get()

    fun set(scope: TraceScope) = holder.set(scope)

    fun clear() = holder.remove()
}

object TraceIds {

    private const val HEX = "0123456789abcdef"

    fun traceId(): String = randomHex(32)

    fun spanId(): String = randomHex(16)

    private fun randomHex(length: Int): String {
        val random = ThreadLocalRandom.current()
        val sb = StringBuilder(length)
        repeat(length) { sb.append(HEX[random.nextInt(16)]) }
        return sb.toString()
    }
}

data class ParsedTraceParent(
    val traceId: String,
    val parentSpanId: String,
    val sampled: Boolean
)

object TraceParent {

    fun parse(header: String?): ParsedTraceParent? {
        if (header.isNullOrBlank()) return null
        val parts = header.trim().split("-")
        if (parts.size < 4) return null
        val traceId = parts[1]
        val parentSpanId = parts[2]
        val flags = parts[3]
        if (traceId.length != 32 || parentSpanId.length != 16) return null
        if (traceId.all { it == '0' } || parentSpanId.all { it == '0' }) return null
        val sampled = runCatching { flags.toInt(16) and 0x01 == 1 }.getOrDefault(true)
        return ParsedTraceParent(traceId.lowercase(), parentSpanId.lowercase(), sampled)
    }

    fun format(traceId: String, spanId: String, sampled: Boolean = true): String {
        val flags = if (sampled) "01" else "00"
        return "00-$traceId-$spanId-$flags"
    }
}
