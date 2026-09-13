package com.dscorp.wispadmin.transport

import org.springframework.core.env.Environment

object RegistrationTimingSupport {
    const val PREFIX = "REG_TIMING"
    const val HEADER = "X-Gf-Reg-Trace"
    const val MDC_TRACE = "regTrace"

    fun enabled(environment: Environment): Boolean {
        if (looksNonProd(environment)) return true
        return environment.getProperty("gigafiber.registration.timing.enabled", Boolean::class.java, false)
    }

    fun looksNonProd(environment: Environment): Boolean {
        val profiles = environment.activeProfiles.map { it.lowercase() }.toSet()
        if (profiles.contains("staging") ||
            profiles.contains("dev") ||
            profiles.contains("local") ||
            profiles.contains("local-prestaging")
        ) {
            return true
        }
        val tag = environment.getProperty("gigafiber.environment.tag").orEmpty().lowercase()
        if (tag == "lpstg") return true
        val ctx = environment.getProperty("server.servlet.context-path").orEmpty().lowercase()
        if (ctx.contains("staging")) return true
        val url = environment.getProperty("spring.datasource.url").orEmpty().lowercase()
        if (url.contains("stg_") ||
            url.contains("ispadmin_dev") ||
            url.contains("ispadmin_staging") ||
            url.contains("prestaging")
        ) {
            return true
        }
        if (url.contains("localhost") || url.contains("127.0.0.1")) return true
        return false
    }

    fun warName(contextPath: String?): String {
        val ctx = contextPath.orEmpty().lowercase()
        if (ctx.contains("oltgateway")) return "gateway"
        if (ctx.contains("acs")) return "acs"
        return "core"
    }

    fun isRegistrationPath(uri: String): Boolean {
        val path = uri.lowercase()
        if (path.contains("/actuator") || path.endsWith("/health") || path.contains("/health")) {
            return false
        }
        return path.contains("/subscription") ||
            path.contains("/api/olt-gateway/") ||
            path.contains("/api/acs/") ||
            path.contains("/onu/")
    }

    fun format(war: String, kind: String, attrs: Map<String, String?>): String {
        val parts = mutableListOf(PREFIX, "war=$war", "kind=$kind")
        attrs.forEach { (key, value) ->
            if (!value.isNullOrBlank()) parts += "$key=$value"
        }
        return parts.joinToString(" ")
    }
}
