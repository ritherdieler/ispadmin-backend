package com.dscorp.wispadmin.wispadmin.trafficclient

import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate

class TrafficHttpClient(
    private val properties: TrafficClientProperties,
    private val restTemplate: RestTemplate,
) {
    fun getJson(path: String, query: String? = null): ResponseEntity<String> {
        val base = properties.internalBaseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "traffic.internal-base-url is blank" }
        val suffix = if (query.isNullOrBlank()) "" else "?$query"
        val headers = HttpHeaders()
        headers.set(CoreTrafficApiKeyFilter.HEADER, properties.apiKey)
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        return restTemplate.exchange(
            "$base$path$suffix",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            String::class.java,
        )
    }

    fun postJson(path: String): ResponseEntity<String> {
        val base = properties.internalBaseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "traffic.internal-base-url is blank" }
        val headers = HttpHeaders()
        headers.set(CoreTrafficApiKeyFilter.HEADER, properties.apiKey)
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        return restTemplate.exchange(
            "$base$path",
            HttpMethod.POST,
            HttpEntity<Void>(headers),
            String::class.java,
        )
    }
}
