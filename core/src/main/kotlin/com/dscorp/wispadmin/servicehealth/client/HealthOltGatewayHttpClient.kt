package com.dscorp.wispadmin.servicehealth.client

import com.dscorp.wispadmin.servicehealth.port.HealthCpeCommand
import com.dscorp.wispadmin.servicehealth.port.HealthCpePort
import com.dscorp.wispadmin.servicehealth.port.HealthCpeTelemetry
import com.dscorp.wispadmin.servicehealth.port.HealthLabOpticalPort
import com.dscorp.wispadmin.servicehealth.port.HealthLabOpticalRefresh
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.port.HealthOnuRef
import com.dscorp.wispadmin.transport.InternalUris
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Instant

@Component
class HealthOltGatewayHttpClient(
    @Value("\${olt.gateway.internal-base-url:}") private val baseUrl: String,
    @Value("\${olt.gateway.api-key:}") private val apiKey: String,
) : HealthOnuPort, HealthLabOpticalPort, HealthCpePort {

    private val objectMapper = ObjectMapper()
    private val restTemplate = RestTemplate(SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(3_000)
        setReadTimeout(30_000)
    })

    override fun findBySn(sn: String): HealthOnuRef? = getRef(query("/api/olt-gateway/health-onus/by-sn", mapOf("sn" to sn)))

    override fun findByExternalId(externalId: String): HealthOnuRef? =
        getRef(query("/api/olt-gateway/health-onus/by-external-id", mapOf("id" to externalId)))

    override fun findByOltBoardPortOnu(oltId: Long, board: Int, port: Int, onuIndex: Int): HealthOnuRef? =
        getRef(query("/api/olt-gateway/health-onus/by-position", mapOf("oltId" to oltId, "board" to board, "port" to port, "onuIndex" to onuIndex)))

    override fun findByOlt(oltId: Long): List<HealthOnuRef> {
        val node = getJson(path("api", "olt-gateway", "health-onus", "by-olt", oltId.toString())) ?: return emptyList()
        if (!node.isArray) return emptyList()
        return node.mapNotNull { it.toRef() }
    }

    override fun findOltIdByName(name: String): Long? {
        val node = getJson(query("/api/olt-gateway/olts/id-by-name", mapOf("name" to name))) ?: return null
        return node.path("id").takeIf { it.isNumber }?.asLong()
    }

    override fun refreshBySn(sn: String): HealthLabOpticalRefresh {
        val node = postJson(path("api", "olt-gateway", "admin", "lab-optical", "refresh"), objectMapper.writeValueAsString(mapOf("sn" to sn)))
            ?: return HealthLabOpticalRefresh(false, error = "gateway_unavailable")
        return HealthLabOpticalRefresh(
            collected = node.path("collected").asBoolean(false),
            unmapped = node.path("unmapped").asBoolean(false),
            error = node.path("error").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    override fun telemetry(sn: String): HealthCpeTelemetry? {
        val node = getJson(path("api", "olt-gateway", "onus", sn, "cpe", "telemetry")) ?: return null
        return HealthCpeTelemetry(
            sn = node.path("sn").asText(sn),
            uniqueExternalId = node.path("uniqueExternalId").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            cpeStatus = node.path("cpeStatus").asText("NA"),
            lastInformAt = node.path("lastInformAt").asText(null)?.takeIf { it.isNotBlank() && it != "null" }?.let {
                runCatching { Instant.parse(it) }.getOrNull()
            },
            productClass = node.path("productClass").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            wanIp = node.path("wanIp").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            ssid24 = node.path("ssid24").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            ssid5 = node.path("ssid5").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            softwareVersion = node.path("softwareVersion").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            wifiAssociated2g = node.path("wifiAssociated2g").takeIf { it.isNumber }?.asInt(),
            wifiAssociated5g = node.path("wifiAssociated5g").takeIf { it.isNumber }?.asInt(),
            wifiAssociatedTotal = node.path("wifiAssociatedTotal").takeIf { it.isNumber }?.asInt(),
            wifiObservedAt = node.path("wifiObservedAt").asText(null)?.takeIf { it.isNotBlank() && it != "null" }?.let {
                runCatching { Instant.parse(it) }.getOrNull()
            },
            wifiQualityStatus = node.path("wifiQualityStatus").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    override fun reboot(sn: String): HealthCpeCommand = command(path("api", "olt-gateway", "onus", sn, "cpe", "reboot"))

    override fun wifiRefresh(sn: String): HealthCpeCommand = command(path("api", "olt-gateway", "onus", sn, "cpe", "wifi-refresh"))

    private fun command(uri: java.net.URI): HealthCpeCommand {
        val node = postJson(uri, "{}")
            ?: return HealthCpeCommand(false, "FAILED", "gateway_unavailable")
        return HealthCpeCommand(
            accepted = node.path("accepted").asBoolean(false),
            status = node.path("status").asText("FAILED"),
            message = node.path("message").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    private fun getRef(uri: java.net.URI): HealthOnuRef? = getJson(uri)?.toRef()

    private fun JsonNode.toRef(): HealthOnuRef? {
        val id = path("id").takeIf { it.isNumber }?.asLong() ?: return null
        return HealthOnuRef(
            id = id,
            sn = path("sn").asText(""),
            externalId = path("externalId").asText(null),
            oltId = path("oltId").takeIf { it.isNumber }?.asLong(),
            oltName = path("oltName").asText(null),
            board = path("board").asInt(0),
            port = path("port").asInt(0),
            onuIndex = path("onuIndex").asInt(0),
            zoneId = path("zoneId").takeIf { it.isNumber }?.asLong(),
        )
    }

    private fun query(path: String, params: Map<String, Any?>) =
        if (baseUrl.isBlank()) java.net.URI.create("http://127.0.0.1/") else InternalUris.uri(baseUrl, path, params)
    private fun path(vararg segments: String) =
        if (baseUrl.isBlank()) java.net.URI.create("http://127.0.0.1/") else InternalUris.path(baseUrl, *segments)

    private fun getJson(uri: java.net.URI): JsonNode? {
        if (baseUrl.isBlank()) return null
        return try {
            val headers = HttpHeaders()
            headers.set("X-Olt-Gateway-Key", apiKey)
            val response = restTemplate.exchange(uri, HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
            val body = response.body ?: return null
            objectMapper.readTree(body)
        } catch (ex: Exception) {
            logger.warn("Health OLT HTTP failed for {}: {}", uri, ex.message)
            null
        }
    }

    private fun postJson(uri: java.net.URI, json: String): JsonNode? {
        if (baseUrl.isBlank()) return null
        return try {
            val headers = HttpHeaders()
            headers.set("X-Olt-Gateway-Key", apiKey)
            headers.set("Content-Type", "application/json")
            val response = restTemplate.exchange(uri, HttpMethod.POST, HttpEntity(json, headers), String::class.java)
            val body = response.body ?: return null
            objectMapper.readTree(body)
        } catch (ex: Exception) {
            logger.warn("Health OLT HTTP POST failed for {}: {}", uri, ex.message)
            null
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(HealthOltGatewayHttpClient::class.java)
    }
}
