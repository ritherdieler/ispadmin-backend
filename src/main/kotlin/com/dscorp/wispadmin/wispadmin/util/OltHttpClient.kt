package com.dscorp.wispadmin.wispadmin.util

import com.dscorp.wispadmin.observability.tracing.TracingInterceptorHolder
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class OltHttpClient(
    private val requestResponseLoggingInterceptor: ClientHttpRequestInterceptor,
    @Value("\${olt.service.base-url}") private val baseUrl: String,
    @Value("\${olt.service.api-key}") private val apiKey: String,
    @Value("\${olt.service.provider:smartolt}") private val provider: String,
    @Value("\${olt.service.auth-header:}") private val authHeaderOverride: String,
    private val restTemplate: RestTemplate = RestTemplate()
) {
    init {
        restTemplate.interceptors.add(requestResponseLoggingInterceptor)
        TracingInterceptorHolder.instance?.let { restTemplate.interceptors.add(it) }
    }

    fun <T> get(url: String, responseType: Class<T>): T {
        val requestEntity = HttpEntity<Any>(authHeaders())
        return restTemplate.exchange("${baseUrl}$url", HttpMethod.GET, requestEntity, responseType).body!!
    }

    fun <T> post(url: String, body: Any? = null, responseType: Class<T>): T {
        val requestEntity = HttpEntity<Any>(body, authHeaders())
        return restTemplate.exchange("${baseUrl}$url", HttpMethod.POST, requestEntity, responseType).body!!
    }

    private fun authHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        headers.set(resolveAuthHeaderName(), apiKey)
        return headers
    }

    private fun resolveAuthHeaderName(): String {
        if (authHeaderOverride.isNotBlank()) {
            return authHeaderOverride
        }
        return if (provider.equals("gateway", ignoreCase = true)) {
            "X-Olt-Gateway-Key"
        } else {
            "X-Token"
        }
    }
}
