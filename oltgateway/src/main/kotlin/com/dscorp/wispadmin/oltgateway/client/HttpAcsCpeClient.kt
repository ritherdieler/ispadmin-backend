package com.dscorp.wispadmin.oltgateway.client

import com.dscorp.wispadmin.oltgateway.config.GatewayCallContext
import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.oltgateway.dto.CpeProvisionStatus
import com.dscorp.wispadmin.oltgateway.dto.CpeTelemetryDto
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate

class HttpAcsCpeClient(
    private val properties: OltGatewayProperties,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    private val router: AcsCallerRouter = AcsCallerRouter(properties),
) : AcsCpeClient {

    override fun provision(request: AcsCpeProvisionRequest): AcsCpeProvisionResponse {
        val node = post("/api/acs/v1/cpe/provision", objectMapper.writeValueAsString(request))
        return AcsCpeProvisionResponse(
            sn = node.path("sn").asText(request.sn),
            status = statusOf(node.path("status").asText("")),
            message = node.path("message").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            deviceId = node.path("deviceId").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    override fun reboot(sn: String): CpeCommandAck {
        val node = post("/api/acs/v1/cpe/${enc(sn)}/reboot", "{}")
        return CpeCommandAck(
            accepted = node.path("accepted").asBoolean(false),
            status = statusOf(node.path("status").asText("")),
            message = node.path("message").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    override fun wifiRefresh(sn: String): CpeCommandAck {
        val node = post("/api/acs/v1/cpe/${enc(sn)}/wifi-refresh", "{}")
        return CpeCommandAck(
            accepted = node.path("accepted").asBoolean(false),
            status = statusOf(node.path("status").asText("")),
            message = node.path("message").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    override fun setWifi(sn: String, request: AcsCpeWifiRequest): CpeCommandAck {
        val node = post("/api/acs/v1/cpe/${enc(sn)}/wifi", objectMapper.writeValueAsString(request))
        return CpeCommandAck(
            accepted = node.path("accepted").asBoolean(false),
            status = statusOf(node.path("status").asText("")),
            message = node.path("message").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    override fun telemetry(sn: String): CpeTelemetryDto? {
        val node = get("/api/acs/v1/cpe/${enc(sn)}/telemetry") ?: return null
        return CpeTelemetryDto(
            sn = node.path("sn").asText(sn),
            uniqueExternalId = node.path("uniqueExternalId").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            cpeStatus = statusOf(node.path("cpeStatus").asText("")),
            lastInformAt = node.path("lastInformAt").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            productClass = node.path("productClass").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            wanIp = node.path("wanIp").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            ssid24 = node.path("ssid24").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            ssid5 = node.path("ssid5").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            softwareVersion = node.path("softwareVersion").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            wifiAssociated2g = node.path("wifiAssociated2g").takeIf { it.isNumber }?.asInt(),
            wifiAssociated5g = node.path("wifiAssociated5g").takeIf { it.isNumber }?.asInt(),
            wifiAssociatedTotal = node.path("wifiAssociatedTotal").takeIf { it.isNumber }?.asInt(),
            wifiObservedAt = node.path("wifiObservedAt").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            wifiQualityStatus = node.path("wifiQualityStatus").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            deviceId = node.path("deviceId").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    override fun status(sn: String): AcsCpeProvisionResponse? {
        val node = get("/api/acs/v1/cpe/${enc(sn)}/status") ?: return null
        return AcsCpeProvisionResponse(
            sn = node.path("sn").asText(sn),
            status = statusOf(node.path("status").asText("")),
            message = node.path("message").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            deviceId = node.path("deviceId").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    override fun accessLayout(sn: String): com.dscorp.wispadmin.oltgateway.dto.CpeAccessLayoutDto? {
        val node = get("/api/acs/v1/cpe/${enc(sn)}/access-layout") ?: return null
        return com.dscorp.wispadmin.oltgateway.dto.CpeAccessLayoutDto(
            sn = node.path("sn").asText(sn),
            productClass = node.path("productClass").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            connectionRequestUrl = node.path("connectionRequestUrl").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            lastInformAt = node.path("lastInformAt").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            wanIpPath = node.path("wanIpPath").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            wanPppPath = node.path("wanPppPath").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
            hasPppPath = node.path("hasPppPath").asBoolean(false),
            wanIpSharesPppSlot = node.path("wanIpSharesPppSlot").asBoolean(false),
        )
    }

    private fun statusOf(raw: String): CpeProvisionStatus =
        try { CpeProvisionStatus.valueOf(raw.uppercase()) }
        catch (_: IllegalArgumentException) { throw com.dscorp.wispadmin.transport.InvalidSubsystemResponse() }

    private fun enc(sn: String) = java.net.URLEncoder.encode(sn, Charsets.UTF_8).replace("+", "%20")

    private fun headers(): HttpHeaders {
        val headers = HttpHeaders()
        headers.set(HEADER, properties.acs.apiKey)
        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        return headers
    }

    private fun base(): String = router.baseUrl(GatewayCallContext.env())

    private fun get(path: String): com.fasterxml.jackson.databind.JsonNode? = try {
        exchange(path, HttpMethod.GET, HttpEntity<Void>(headers()))
    } catch (ex: org.springframework.web.client.HttpClientErrorException) {
        if (ex.statusCode == org.springframework.http.HttpStatus.NOT_FOUND) null else throw ex
    }

    private fun post(path: String, body: String): com.fasterxml.jackson.databind.JsonNode =
        exchange(path, HttpMethod.POST, HttpEntity(body, headers()))

    private fun exchange(path: String, method: HttpMethod, entity: HttpEntity<*>): com.fasterxml.jackson.databind.JsonNode {
        val root = base()
        if (root.isEmpty()) throw org.springframework.web.client.RestClientException("ACS URL is not configured")
        val response = restTemplate.exchange(java.net.URI.create("$root$path"), method, entity, String::class.java)
        com.dscorp.wispadmin.transport.InternalJson.validate(response)
        val node = objectMapper.readTree(response.body)
        if (node == null || !node.isObject) throw com.dscorp.wispadmin.transport.InvalidSubsystemResponse()
        return node
    }

    companion object {
        const val HEADER = "X-Acs-Key"
    }
}
