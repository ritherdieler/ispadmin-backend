package com.dscorp.wispadmin.traffic.client

import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.dscorp.wispadmin.traffic.port.TrafficDirectoryPort
import com.dscorp.wispadmin.traffic.port.TrafficDirectoryTarget
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class HttpTrafficDirectoryClient(
    private val properties: TrafficProperties,
    private val objectMapper: ObjectMapper,
) : TrafficDirectoryPort {

    private val restTemplate = RestTemplate()
    private val lock = Any()
    @Volatile
    private var cached: List<TrafficDirectoryTarget> = emptyList()
    @Volatile
    private var cachedAtMs: Long = 0

    override fun list(): List<TrafficDirectoryTarget> {
        val now = System.currentTimeMillis()
        val ttlMs = properties.directoryTtlSeconds.coerceAtLeast(0) * 1000
        if (ttlMs > 0 && cachedAtMs > 0 && now - cachedAtMs < ttlMs) {
            return cached
        }
        return try {
            val fetched = fetch()
            synchronized(lock) {
                cached = fetched
                cachedAtMs = System.currentTimeMillis()
            }
            fetched
        } catch (ex: Exception) {
            if (cachedAtMs > 0) {
                logger.warn("Traffic directory refresh failed; using cache: {}", ex.message)
                cached
            } else {
                throw ex
            }
        }
    }

    private fun fetch(): List<TrafficDirectoryTarget> {
        val base = properties.coreBaseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return emptyList()
        val headers = HttpHeaders()
        headers.set(TRAFFIC_KEY_HEADER, properties.apiKey)
        val response = restTemplate.exchange(
            "$base/internal/traffic/targets",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
            String::class.java,
        )
        val body = response.body ?: return emptyList()
        return objectMapper.readValue(body, Array<TrafficDirectoryTarget>::class.java).toList()
    }

    companion object {
        const val TRAFFIC_KEY_HEADER = "X-Traffic-Key"
        private val logger = LoggerFactory.getLogger(HttpTrafficDirectoryClient::class.java)
    }
}
