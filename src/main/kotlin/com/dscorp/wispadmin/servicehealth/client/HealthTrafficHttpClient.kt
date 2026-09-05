package com.dscorp.wispadmin.servicehealth.client

import com.dscorp.wispadmin.servicehealth.port.HealthTrafficAnomaly
import com.dscorp.wispadmin.servicehealth.port.HealthTrafficPort
import com.dscorp.wispadmin.servicehealth.port.HealthTrafficRun
import com.dscorp.wispadmin.servicehealth.port.HealthTrafficSample
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime

@Component
class HealthTrafficHttpClient(
    @Value("\${traffic.internal-base-url:}") private val baseUrl: String,
    @Value("\${traffic.api-key:}") private val apiKey: String,
) : HealthTrafficPort {

    private val objectMapper = ObjectMapper()

    private val restTemplate = RestTemplate(SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(3_000)
        setReadTimeout(5_000)
    })

    override fun latestSample(subscriptionId: Int): HealthTrafficSample? {
        val node = getJson("/api/traffic/v1/by-subscription/$subscriptionId/latest") ?: return null
        if (node.path("sampleStatus").isMissingNode && node.path("bucketStart").isNull) return null
        if (node.path("sampleStatus").isNull && node.path("bucketStart").isNull) return null
        val status = node.path("sampleStatus").asText(null) ?: return null
        return HealthTrafficSample(
            id = node.path("id").takeIf { it.isNumber }?.asLong(),
            hostDeviceId = node.path("hostDeviceId").asInt(0),
            collectedAt = node.path("collectedAt").asText(null)?.let { LocalDateTime.parse(it) },
            sampleStatus = status,
            avgMbpsDown = node.path("avgMbpsDown").takeIf { it.isNumber }?.asDouble(),
            avgMbpsUp = node.path("avgMbpsUp").takeIf { it.isNumber }?.asDouble(),
            queueId = node.path("queueId").asText(null),
        )
    }

    override fun latestRun(hostDeviceId: Int): HealthTrafficRun? {
        val node = getJson("/api/traffic/v1/routers/$hostDeviceId/latest-run") ?: return null
        val status = node.path("status").asText(null) ?: return null
        return HealthTrafficRun(
            id = node.path("id").takeIf { it.isNumber }?.asLong(),
            completedAt = node.path("completedAt").asText(null)?.let { LocalDateTime.parse(it) },
            status = status,
        )
    }

    override fun findAnomalyChanges(after: LocalDateTime, afterId: Long, page: Pageable): List<HealthTrafficAnomaly> {
        val node = getJson("/api/traffic/v1/anomalies/changes?after=$after&afterId=$afterId&size=${page.pageSize}")
            ?: return emptyList()
        if (!node.isArray) return emptyList()
        return node.mapNotNull { item ->
            val id = item.path("id").takeIf { it.isNumber }?.asLong() ?: return@mapNotNull null
            HealthTrafficAnomaly(
                id = id,
                subscriptionId = item.path("subscriptionId").takeIf { it.isNumber }?.asInt(),
                hostDeviceId = item.path("hostDeviceId").takeIf { it.isNumber }?.asInt(),
                eventStatus = item.path("eventStatus").asText("OPEN"),
                anomalyType = item.path("anomalyType").asText(""),
                lastEvaluatedAt = item.path("lastEvaluatedAt").asText(null)?.let { LocalDateTime.parse(it) } ?: after,
                coveragePct = item.path("coveragePct").asDouble(0.0),
                confidence = item.path("confidence").asDouble(0.0),
                evidenceJson = item.path("evidenceJson").asText(null),
            )
        }
    }

    override fun minimumCoveragePct(): Double {
        val node = getJson("/api/traffic/v1/config") ?: return 80.0
        return node.path("minimumCoveragePct").asDouble(80.0)
    }

    private fun getJson(path: String): JsonNode? {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return null
        return try {
            val headers = HttpHeaders()
            headers.set("X-Traffic-Key", apiKey)
            val response = restTemplate.exchange(
                "$base$path",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                String::class.java,
            )
            val body = response.body ?: return null
            objectMapper.readTree(body)
        } catch (ex: Exception) {
            logger.warn("Health traffic HTTP failed for {}: {}", path, ex.message)
            null
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(HealthTrafficHttpClient::class.java)
    }
}
