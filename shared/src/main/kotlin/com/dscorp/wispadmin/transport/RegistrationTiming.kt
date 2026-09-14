package com.dscorp.wispadmin.transport

import org.slf4j.LoggerFactory
import org.slf4j.MDC

class RegistrationTiming(
    private val enabled: Boolean,
    private val war: String,
    private val sink: (String) -> Unit = { log.info(it) },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    val isEnabled: Boolean get() = enabled

    fun <T> span(name: String, attrs: Map<String, String?> = emptyMap(), block: () -> T): T {
        if (!enabled) return block()
        val start = clock()
        var status = "ok"
        try {
            return block()
        } catch (ex: Exception) {
            status = "error"
            throw ex
        } finally {
            emit(
                kind = "span",
                attrs = attrs + mapOf(
                    "name" to name,
                    "status" to status,
                    "ms" to (clock() - start).toString(),
                ),
            )
        }
    }

    fun httpIn(method: String, path: String, status: Int, ms: Long) {
        emit(
            kind = "http.in",
            attrs = mapOf(
                "method" to method,
                "path" to path,
                "status" to status.toString(),
                "ms" to ms.toString(),
            ),
        )
    }

    fun httpOut(method: String, path: String, status: Int?, ms: Long) {
        emit(
            kind = "http.out",
            attrs = mapOf(
                "method" to method,
                "path" to path,
                "status" to status?.toString(),
                "ms" to ms.toString(),
            ),
        )
    }

    private fun emit(kind: String, attrs: Map<String, String?>) {
        if (!enabled) return
        val withTrace = attrs.toMutableMap()
        if (withTrace["trace"].isNullOrBlank()) {
            withTrace["trace"] = MDC.get(RegistrationTimingSupport.MDC_TRACE)
        }
        sink(RegistrationTimingSupport.format(war, kind, withTrace))
    }

    companion object {
        private val log = LoggerFactory.getLogger(RegistrationTimingSupport.PREFIX)
        val NOOP = RegistrationTiming(enabled = false, war = "core", sink = {})
    }
}
