package com.dscorp.wispadmin.oltgateway.client

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.dto.CpeProvisionStatus
import com.dscorp.wispadmin.oltgateway.dto.CpeTelemetryDto
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate

class HttpAcsCpeClient(
    private val properties: OltGatewayProperties,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
) : AcsCpeClient {
    private val log = LoggerFactory.getLogger(HttpAcsCpeClient::class.java)

    override fun provision(request: AcsCpeProvisionRequest): AcsCpeProvisionResponse {
        val node = post("/api/acs/v1/cpe/provision", objectMapper.writeValueAsString(request))
            ?: return AcsCpeProvisionResponse(request.sn, CpeProvisionStatus.FAILED, "ACS unavailable")
        return AcsCpeProvisionResponse(
            sn = node.path("sn").asText(request.sn),
            status = statusOf(node.path("status").asText("PENDING")),
            message = node.path("message").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            deviceId = node.path("deviceId").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    override fun reboot(sn: String): CpeCommandAck {
        val node = post("/api/acs/v1/cpe/${enc(sn)}/reboot", "{}")
            ?: return CpeCommandAck(false, CpeProvisionStatus.FAILED, "ACS unavailable")
        return CpeCommandAck(
            accepted = node.path("accepted").asBoolean(false),
            status = statusOf(node.path("status").asText("PENDING")),
            message = node.path("message").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    override fun wifiRefresh(sn: String): CpeCommandAck {
        val node = post("/api/acs/v1/cpe/${enc(sn)}/wifi-refresh", "{}")
            ?: return CpeCommandAck(false, CpeProvisionStatus.FAILED, "ACS unavailable")
        return CpeCommandAck(
            accepted = node.path("accepted").asBoolean(false),
            status = statusOf(node.path("status").asText("PENDING")),
            message = node.path("message").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    override fun telemetry(sn: String): CpeTelemetryDto? {
        val node = get("/api/acs/v1/cpe/${enc(sn)}/telemetry") ?: return null
        return CpeTelemetryDto(
            sn = node.path("sn").asText(sn),
            uniqueExternalId = node.path("uniqueExternalId").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            cpeStatus = statusOf(node.path("cpeStatus").asText("PENDING")),
            lastInformAt = node.path("lastInformAt").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            productClass = node.path("productClass").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            wanIp = node.path("wanIp").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            ssid24 = node.path("ssid24").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            ssid5 = node.path("ssid5").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            softwareVersion = node.path("softwareVersion").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    override fun status(sn: String): AcsCpeProvisionResponse? {
        val node = get("/api/acs/v1/cpe/${enc(sn)}/status") ?: return null
        return AcsCpeProvisionResponse(
            sn = node.path("sn").asText(sn),
            status = statusOf(node.path("status").asText("PENDING")),
            message = node.path("message").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            deviceId = node.path("deviceId").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    private fun statusOf(raw: String): CpeProvisionStatus =
        runCatching { CpeProvisionStatus.valueOf(raw.uppercase()) }.getOrDefault(CpeProvisionStatus.PENDING)

    private fun enc(sn: String) = java.net.URLEncoder.encode(sn, Charsets.UTF_8)

    private fun headers(): HttpHeaders {
        val headers = HttpHeaders()
        headers.set(HEADER, properties.acs.apiKey)
        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        return headers
    }

    private fun base(): String = properties.acs.internalBaseUrl.trim().trimEnd('/')

    private fun get(path: String): com.fasterxml.jackson.databind.JsonNode? {
        val root = base()
        if (root.isEmpty()) return null
        return try {
            val response = restTemplate.exchange(
                "$root$path",
                HttpMethod.GET,
                HttpEntity<Void>(headers()),
                String::class.java,
            )
            objectMapper.readTree(response.body ?: return null)
        } catch (ex: Exception) {
            log.warn("ACS GET {} failed: {}", path, ex.message)
            null
        }
    }

    private fun post(path: String, body: String): com.fasterxml.jackson.databind.JsonNode? {
        val root = base()
        if (root.isEmpty()) return null
        return try {
            val response = restTemplate.exchange(
                "$root$path",
                HttpMethod.POST,
                HttpEntity(body, headers()),
                String::class.java,
            )
            objectMapper.readTree(response.body ?: return null)
        } catch (ex: Exception) {
            log.warn("ACS POST {} failed: {}", path, ex.message)
            null
        }
    }

    companion object {
        const val HEADER = "X-Acs-Key"
    }
}
