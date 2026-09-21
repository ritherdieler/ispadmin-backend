package com.dscorp.wispadmin.oltgateway.client

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class AcsCallerRouter(
    private val properties: OltGatewayProperties,
) {
    fun baseUrl(envHeader: String?): String {
        val env = envHeader?.trim()?.lowercase().orEmpty()
        if (env.isEmpty()) {
            if (properties.acs.requireCaller) unresolved()
            return legacyUrl()
        }
        val url = when (env) {
            "prod" -> firstUrl(properties.acs.baseUrlProd, properties.acs.internalBaseUrl)
            "stg", "staging", "lpstg" -> properties.acs.baseUrlStaging.trim()
            else -> ""
        }
        if (url.isBlank()) unresolved()
        return url.trimEnd('/')
    }

    private fun legacyUrl(): String {
        val url = properties.acs.internalBaseUrl.trim()
        if (url.isEmpty()) unresolved()
        return url.trimEnd('/')
    }

    private fun firstUrl(primary: String, fallback: String): String {
        val chosen = primary.trim().ifBlank { fallback.trim() }
        return chosen
    }

    private fun unresolved(): Nothing =
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ACS caller is unresolved")
}
