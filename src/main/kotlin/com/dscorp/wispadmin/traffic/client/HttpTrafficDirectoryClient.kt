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

    private val restTemplate = RestTemplate(org.springframework.http.client.SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(3_000)
        setReadTimeout(8_000)
    })
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
            if (cachedAtMs > 0 && now - cachedAtMs <= properties.directoryMaxStaleSeconds * 1000) {
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
        val collected = mutableListOf<TrafficDirectoryTarget>()
        var after = 0
        repeat(10_000) {
            val response = restTemplate.exchange(
                "$base/internal/traffic/targets/page?after=$after&size=200",
                HttpMethod.GET, HttpEntity<Void>(headers), String::class.java,
            )
            val root = objectMapper.readTree(response.body ?: error("Empty target page"))
                ?: error("Empty target page")
            check(root.path("items").isArray && root.has("nextCursor")) { "Invalid target page" }
            val items = root.path("items").map { objectMapper.treeToValue(it, TrafficDirectoryTarget::class.java) }
            check(items.size <= 200 && items.all { it.subscriptionId > after }) { "Invalid target order" }
            check(items.zipWithNext().all { (a, b) -> a.subscriptionId < b.subscriptionId }) { "Invalid target order" }
            collected.addAll(items)
            val cursor = root.path("nextCursor")
            if (cursor.isNull) return collected
            check(cursor.isIntegralNumber && cursor.canConvertToInt()) { "Invalid target cursor" }
            val next = cursor.asInt()
            check(next > after && items.all { it.subscriptionId <= next }) { "Target cursor did not advance" }
            after = next
        }
        error("Target directory exceeds page limit")
    }

    companion object {
        const val TRAFFIC_KEY_HEADER = "X-Traffic-Key"
        private val logger = LoggerFactory.getLogger(HttpTrafficDirectoryClient::class.java)
    }
}
