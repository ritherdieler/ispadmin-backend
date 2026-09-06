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
        val headers = HttpHeaders()
        headers.set(HEADER, properties.apiKey)
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        return com.dscorp.wispadmin.transport.InternalJson.validate(restTemplate.exchange(
            java.net.URI.create("$base$path$suffix"),
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            String::class.java,
        ))
    }

    fun postJson(path: String, query: String? = null): ResponseEntity<String> {
        val base = properties.internalBaseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "olt.gateway.internal-base-url is blank" }
        val suffix = if (query.isNullOrBlank()) "" else "?$query"
        val headers = HttpHeaders()
        headers.set(HEADER, properties.apiKey)
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        return com.dscorp.wispadmin.transport.InternalJson.validate(restTemplate.exchange(
            java.net.URI.create("$base$path$suffix"),
            HttpMethod.POST,
            HttpEntity<Void>(headers),
            String::class.java,
        ))
    }

    fun postJsonBody(path: String, body: String): ResponseEntity<String> {
        val base = properties.internalBaseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "olt.gateway.internal-base-url is blank" }
        val headers = HttpHeaders()
        headers.set(HEADER, properties.apiKey)
        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        return com.dscorp.wispadmin.transport.InternalJson.validate(restTemplate.exchange(
            java.net.URI.create("$base$path"),
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java,
        ))
    }

    fun deleteJson(path: String): ResponseEntity<String> {
        val base = properties.internalBaseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "olt.gateway.internal-base-url is blank" }
        val headers = HttpHeaders()
        headers.set(HEADER, properties.apiKey)
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        return com.dscorp.wispadmin.transport.InternalJson.validate(restTemplate.exchange(
            java.net.URI.create("$base$path"),
            HttpMethod.DELETE,
            HttpEntity<Void>(headers),
            String::class.java,
        ))
    }

    fun postForm(path: String, form: org.springframework.util.MultiValueMap<String, String>): ResponseEntity<String> {
        val base = properties.internalBaseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "olt.gateway.internal-base-url is blank" }
        val headers = HttpHeaders()
        headers.set(HEADER, properties.apiKey)
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        return com.dscorp.wispadmin.transport.InternalJson.validate(restTemplate.exchange(
            java.net.URI.create("$base$path"),
            HttpMethod.POST,
            HttpEntity(form, headers),
            String::class.java,
        ))
    }

    companion object {
        const val HEADER = "X-Olt-Gateway-Key"
    }
}
