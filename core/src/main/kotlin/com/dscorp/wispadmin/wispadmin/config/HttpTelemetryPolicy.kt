package com.dscorp.wispadmin.wispadmin.config

object HttpTelemetryPolicy {
    private val INTERNAL_SUBSYSTEM_PREFIXES = listOf("/api/olt-gateway/", "/api/acs/", "/api/traffic/")
    private val CONTEXT_PREFIX = Regex("^/ispadmin(-staging)?(?=/)")

    fun shouldReportHttpError(uri: String, status: Int): Boolean {
        val path = withoutContext(uri)
        if (path == "/error") return false
        return status >= 500 || !isInternalSubsystem(path)
    }

    fun forceRetainSpan(route: String, status: Int): Boolean {
        if (status >= 500) return true
        return status >= 400 && !isInternalSubsystem(withoutContext(route))
    }

    private fun isInternalSubsystem(path: String) = INTERNAL_SUBSYSTEM_PREFIXES.any { path.startsWith(it) }

    private fun withoutContext(uri: String) = uri.replace(CONTEXT_PREFIX, "")
}
