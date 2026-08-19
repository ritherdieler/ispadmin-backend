package com.dscorp.wispadmin.wispadmin.util

import com.dscorp.wispadmin.observability.tracing.TracingInterceptorHolder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestTemplate

object HttpClient {

    fun <T> get(baseUrl: String, apiKey: String, url: String, responseType: Class<T>): T {
        val restTemplate = restTemplate()
        val headers = HttpHeaders()
        headers.set("X-Token", apiKey)
        val requestEntity = HttpEntity<Any>(headers)
        return restTemplate.exchange("$baseUrl$url", org.springframework.http.HttpMethod.GET, requestEntity, responseType).body!!
    }

    fun <T> post(baseUrl: String, apiKey: String, url: String, body: Any? = null, responseType: Class<T>): T {
        val restTemplate = restTemplate()
        val headers = HttpHeaders()
        headers.set("X-Token", apiKey)
        val requestEntity = HttpEntity<Any>(body, headers)
        return restTemplate.exchange("$baseUrl$url", org.springframework.http.HttpMethod.POST, requestEntity, responseType).body!!
    }

    private fun restTemplate(): RestTemplate {
        val restTemplate = RestTemplate()
        restTemplate.interceptors.add(RequestResponseLoggingInterceptor())
        TracingInterceptorHolder.instance?.let { restTemplate.interceptors.add(it) }
        return restTemplate
    }
}
