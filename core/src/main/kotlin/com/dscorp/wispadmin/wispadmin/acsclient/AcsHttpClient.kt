package com.dscorp.wispadmin.wispadmin.acsclient

import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

class AcsHttpClient(
    private val properties: AcsClientProperties,
    private val restTemplate: RestTemplate,
) {
    fun getJson(path: String): ResponseEntity<String> {
        val base = properties.internalBaseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "acs.internal-base-url is blank" }
        val headers = HttpHeaders()
        headers.set(HEADER, properties.apiKey)
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        return com.dscorp.wispadmin.transport.InternalJson.validate(
            restTemplate.exchange(
                java.net.URI.create("$base$path"),
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                String::class.java,
            )
        )
    }

    fun postJson(path: String, body: String): ResponseEntity<String> {
        val base = properties.internalBaseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "acs.internal-base-url is blank" }
        val headers = HttpHeaders()
        headers.set(HEADER, properties.apiKey)
        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        return com.dscorp.wispadmin.transport.InternalJson.validate(
            restTemplate.exchange(
                java.net.URI.create("$base$path"),
                HttpMethod.POST,
                HttpEntity(body, headers),
                String::class.java,
            )
        )
    }

    companion object {
        const val HEADER = "X-Acs-Key"
    }
}
