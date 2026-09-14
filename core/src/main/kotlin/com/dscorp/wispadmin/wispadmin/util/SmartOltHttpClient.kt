package com.dscorp.wispadmin.wispadmin.util

import com.dscorp.wispadmin.wispadmin.config.OltServiceProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class SmartOltHttpClient(
    private val properties: OltServiceProperties,
    @Qualifier("smartOltRestTemplate") private val restTemplate: RestTemplate,
) {

    fun <T> get(url: String, responseType: Class<T>): T =
        exchange(url, HttpMethod.GET, null, responseType)

    fun <T> post(url: String, body: Any? = null, responseType: Class<T>): T =
        exchange(url, HttpMethod.POST, body, responseType)

    private fun <T> exchange(url: String, method: HttpMethod, body: Any?, responseType: Class<T>): T {
        val headers = HttpHeaders().apply { set(TOKEN_HEADER, properties.apiKey) }
        val entity = if (body == null) HttpEntity<Any>(headers) else HttpEntity<Any>(body, headers)
        return restTemplate.exchange(absoluteUrl(url), method, entity, responseType).body!!
    }

    private fun absoluteUrl(url: String): String =
        "${properties.baseUrl.trimEnd('/')}/${url.trimStart('/')}"

    private companion object {
        const val TOKEN_HEADER = "X-Token"
    }
}
