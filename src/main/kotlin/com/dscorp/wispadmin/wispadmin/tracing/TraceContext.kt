package com.dscorp.wispadmin.wispadmin.tracing

object TraceContext {
    const val HEADER_TRACEPARENT = "traceparent"
    const val HEADER_SESSION = "X-Obs-Session-Id"
    const val ATTRIBUTE_SESSION = "obsSessionId"
    const val ATTRIBUTE_TRACE_ID = "obsTraceId"
    const val MDC_KEY = "traceId"
}
