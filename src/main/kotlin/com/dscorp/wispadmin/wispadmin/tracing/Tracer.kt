package com.dscorp.wispadmin.wispadmin.tracing

import org.springframework.stereotype.Component

interface Tracer {
    fun <T> span(
        name: String,
        kind: String = "INTERNAL",
        tags: Map<String, Any?>? = null,
        block: () -> T
    ): T
}

@Component
class NoOpTracer : Tracer {
    override fun <T> span(
        name: String,
        kind: String,
        tags: Map<String, Any?>?,
        block: () -> T
    ): T = block()
}
