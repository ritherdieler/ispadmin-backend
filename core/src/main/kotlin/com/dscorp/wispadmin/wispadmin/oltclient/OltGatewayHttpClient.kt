package com.dscorp.wispadmin.wispadmin.oltclient

import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

class OltGatewayHttpClient(
    private val properties: OltGatewayClientProperties,
    private val restTemplate: RestTemplate,
) {
    fun getJson(path: String, query: String? = null): ResponseEntity<String> {
        val base = properties.internalBaseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "olt.gateway.internal-base-url is blank" }
        val suffix = if (query.isNullOrBlank()) "" else "?$query"
        return com.dscorp.wispadmin.transport.InternalJson.validate(restTemplate.exchange(
            java.net.URI.create("$base$path$suffix"),
            HttpMethod.GET,
            HttpEntity<Void>(headers()),
            String::class.java,
        ))
    }

    fun postJson(path: String, query: String? = null): ResponseEntity<String> {
        val base = properties.internalBaseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "olt.gateway.internal-base-url is blank" }
        val suffix = if (query.isNullOrBlank()) "" else "?$query"
        return com.dscorp.wispadmin.transport.InternalJson.validate(restTemplate.exchange(
            java.net.URI.create("$base$path$suffix"),
            HttpMethod.POST,
            HttpEntity<Void>(headers()),
            String::class.java,
        ))
    }

    fun postJsonBody(path: String, body: String): ResponseEntity<String> {
        val base = properties.internalBaseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "olt.gateway.internal-base-url is blank" }
        return com.dscorp.wispadmin.transport.InternalJson.validate(restTemplate.exchange(
            java.net.URI.create("$base$path"),
            HttpMethod.POST,
            HttpEntity(body, headers(MediaType.APPLICATION_JSON)),
            String::class.java,
        ))
    }

    fun deleteJson(path: String): ResponseEntity<String> {
        val base = properties.internalBaseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "olt.gateway.internal-base-url is blank" }
        return com.dscorp.wispadmin.transport.InternalJson.validate(restTemplate.exchange(
            java.net.URI.create("$base$path"),
            HttpMethod.DELETE,
            HttpEntity<Void>(headers()),
            String::class.java,
        ))
    }

    fun postForm(path: String, form: org.springframework.util.MultiValueMap<String, String>): ResponseEntity<String> {
        val base = properties.internalBaseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "olt.gateway.internal-base-url is blank" }
        return com.dscorp.wispadmin.transport.InternalJson.validate(restTemplate.exchange(
            java.net.URI.create("$base$path"),
            HttpMethod.POST,
            HttpEntity(form, headers(MediaType.APPLICATION_FORM_URLENCODED)),
            String::class.java,
        ))
    }

    private fun headers(contentType: MediaType? = null): HttpHeaders {
        val headers = HttpHeaders()
        headers.set(HEADER, properties.apiKey)
        headers.set(ENV_HEADER, properties.callerEnv.trim().ifBlank { "prod" })
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        if (contentType != null) headers.contentType = contentType
        return headers
    }

    companion object {
        const val HEADER = "X-Olt-Gateway-Key"
        const val ENV_HEADER = "X-Gigafiber-Env"
    }
}
