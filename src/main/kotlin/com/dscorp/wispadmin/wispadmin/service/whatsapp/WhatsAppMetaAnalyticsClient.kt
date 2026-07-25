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
import java.time.Instant

@Service
class WhatsAppMetaAnalyticsClient(
    private val whatsAppProperties: WhatsAppProperties,
    tracingInterceptor: com.dscorp.wispadmin.observability.tracing.TracingClientHttpRequestInterceptor
) {

    private val log = LoggerFactory.getLogger(WhatsAppMetaAnalyticsClient::class.java)
    private val restTemplate = RestTemplate().apply { interceptors.add(tracingInterceptor) }
    private val objectMapper = ObjectMapper()

    fun fetchMessagingAnalytics(start: Instant, end: Instant, granularity: String = "DAY"): JsonNode {
        val fields = "analytics.start(${start.epochSecond}).end(${end.epochSecond}).granularity($granularity)"
        return fetchWabaField(fields)
    }

    fun fetchConversationAnalytics(start: Instant, end: Instant, granularity: String = "DAY"): JsonNode {
        val fields = "conversation_analytics.start(${start.epochSecond}).end(${end.epochSecond}).granularity($granularity)"
        return fetchWabaField(fields)
    }

    fun fetchPricingAnalytics(start: Instant, end: Instant, granularity: String = "DAY"): JsonNode {
        val fields = "pricing_analytics.start(${start.epochSecond}).end(${end.epochSecond}).granularity($granularity)"
        return fetchWabaField(fields)
    }

    fun fetchTemplateAnalytics(
        templateIds: List<String>,
        start: Instant,
        end: Instant
    ): JsonNode {
        if (templateIds.isEmpty()) return objectMapper.createObjectNode()
        val ids = templateIds.take(10).joinToString(",")
        val fields = "template_analytics.template_ids([$ids]).start(${start.epochSecond}).end(${end.epochSecond})"
        return fetchWabaField(fields)
    }

    fun fetchMessageTemplates(): JsonNode {
        val url = "${whatsAppProperties.businessAccountUrl()}/message_templates?limit=100"
        return exchange(url)
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
}
