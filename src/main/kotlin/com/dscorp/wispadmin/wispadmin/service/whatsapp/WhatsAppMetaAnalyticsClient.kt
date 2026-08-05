package com.dscorp.wispadmin.wispadmin.service.whatsapp

import com.dscorp.wispadmin.wispadmin.config.WhatsAppProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.time.Instant

@Service
class WhatsAppMetaAnalyticsClient(
    private val whatsAppProperties: WhatsAppProperties,
    tracingInterceptor: com.dscorp.wispadmin.observability.tracing.TracingClientHttpRequestInterceptor
) {

    private val log = LoggerFactory.getLogger(WhatsAppMetaAnalyticsClient::class.java)
    private val restTemplate = RestTemplate().apply { interceptors.add(tracingInterceptor) }
    private val objectMapper = ObjectMapper()

    private val phoneNumberHealthTtl = Duration.ofMinutes(5)
    @Volatile
    private var cachedPhoneNumberHealth: JsonNode? = null
    @Volatile
    private var cachedPhoneNumberHealthAt: Instant? = null

    fun fetchMessagingAnalytics(start: Instant, end: Instant, granularity: String = "DAY"): JsonNode {
        val fields = "analytics.start(${start.epochSecond}).end(${end.epochSecond}).granularity($granularity)"
        return fetchWabaField(fields)
    }

    fun fetchConversationAnalytics(start: Instant, end: Instant, granularity: String = "DAILY"): JsonNode {
        val fields =
            "conversation_analytics.start(${start.epochSecond}).end(${end.epochSecond})" +
                ".granularity($granularity).metric_types([\"CONVERSATION\",\"COST\"])"
        return fetchWabaField(fields)
    }

    fun fetchPricingAnalytics(start: Instant, end: Instant, granularity: String = "DAILY"): JsonNode {
        val fields =
            "pricing_analytics.start(${start.epochSecond}).end(${end.epochSecond})" +
                ".granularity($granularity).dimensions([\"PRICING_CATEGORY\",\"PRICING_TYPE\",\"TIER\",\"COUNTRY\"])"
        return fetchWabaField(fields)
    }

    fun fetchTemplateAnalytics(
        templateIds: List<String>,
        start: Instant,
        end: Instant
    ): JsonNode {
        if (templateIds.isEmpty()) return objectMapper.createObjectNode()
        val ids = templateIds.take(10).joinToString(",")
        val url =
            "${whatsAppProperties.businessAccountUrl()}/template_analytics" +
                "?start=${start.epochSecond}&end=${end.epochSecond}" +
                "&granularity=DAILY" +
                "&metric_types=SENT,DELIVERED,READ,CLICKED" +
                "&template_ids=[$ids]"
        return exchange(url)
    }

    fun fetchMessageTemplates(): JsonNode {
        val url = "${whatsAppProperties.businessAccountUrl()}/message_templates?limit=100"
        return exchange(url)
    }

    fun fetchPhoneNumberHealth(): JsonNode {
        return fetchWithCache(Instant.now()) {
            val url = "${whatsAppProperties.graphApiBaseUrl()}/${whatsAppProperties.phoneNumberId}" +
                "?fields=messaging_limit_tier,quality_rating"
            exchange(url)
        }
    }

    internal fun fetchWithCache(now: Instant, fetcher: () -> JsonNode): JsonNode {
        val fetchedAt = cachedPhoneNumberHealthAt
        val cached = cachedPhoneNumberHealth
        if (cached != null && fetchedAt != null && Duration.between(fetchedAt, now) < phoneNumberHealthTtl) {
            return cached
        }
        val fresh = fetcher()
        cachedPhoneNumberHealth = fresh
        cachedPhoneNumberHealthAt = now
        return fresh
    }

    private fun fetchWabaField(fields: String): JsonNode {
        val url = "${whatsAppProperties.businessAccountUrl()}?fields=$fields"
        return exchange(url)
    }

    private fun exchange(url: String): JsonNode {
        if (!whatsAppProperties.isConfigured()) {
            log.debug("WhatsApp Cloud API no configurada; analytics Meta omitidos")
            return objectMapper.createObjectNode()
        }

        return runCatching {
            val headers = HttpHeaders()
            headers.setBearerAuth(whatsAppProperties.accessToken.trim())
            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                String::class.java
            )
            val body = response.body ?: "{}"
            objectMapper.readTree(body)
        }.getOrElse { error ->
            log.warn("Fallo al consultar analytics Meta: {}", error.message)
            objectMapper.createObjectNode()
        }
    }

    companion object {
        fun parseMessagingLimitTier(node: JsonNode): String? =
            node.path("messaging_limit_tier").takeIf { it.isTextual }?.asText()

        fun parseQualityRating(node: JsonNode): String? =
            node.path("quality_rating").takeIf { it.isTextual }?.asText()
    }
}
