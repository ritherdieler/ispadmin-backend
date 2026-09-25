package com.dscorp.wispadmin.wispadmin.service.provisioningv2

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.client.RestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

class ProvisioningTelemetryDelivery(
    private val http: RestTemplate,
    private val json: ObjectMapper,
    private val baseUrl: String,
    private val apiKey: String,
) {
    init {
        require(java.net.URI(baseUrl).scheme in setOf("http", "https") && apiKey.isNotBlank())
    }

    fun send(deliveryId: String, payload: String): Boolean {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Obs-Api-Key", apiKey)
            set("X-Obs-Delivery-Id", deliveryId)
        }
        val response = http.postForEntity("${baseUrl.trimEnd('/')}/observability/events", HttpEntity(payload, headers), String::class.java)
        val body = response.body?.let(json::readTree) ?: return false
        return response.statusCode.is2xxSuccessful && response.headers.getFirst("X-Obs-Delivery-Id") == deliveryId &&
            body.path("accepted").asInt() == 1 && body.path("rejected").asInt(-1) == 0
    }
}
