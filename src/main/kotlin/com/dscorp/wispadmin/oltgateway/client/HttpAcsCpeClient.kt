package com.dscorp.wispadmin.oltgateway.client

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

    override fun listProfiles(): org.springframework.http.ResponseEntity<String> =
        exchangeRaw("/api/acs/v1/profiles", HttpMethod.GET, HttpEntity<Void>(headers()))

    override fun previewProfile(body: String): org.springframework.http.ResponseEntity<String> =
        exchangeRaw("/api/acs/v1/profiles/preview", HttpMethod.POST, HttpEntity(body, headers()))

    override fun importProfile(body: String): org.springframework.http.ResponseEntity<String> =
        exchangeRaw("/api/acs/v1/profiles/import", HttpMethod.POST, HttpEntity(body, headers()))

    override fun deleteProfile(productClass: String): org.springframework.http.ResponseEntity<String> {
        val uri = com.dscorp.wispadmin.transport.InternalUris.path(base(), "api", "acs", "v1", "profiles", productClass)
        val response = try {
            restTemplate.exchange(uri, HttpMethod.DELETE, HttpEntity<Void>(headers()), String::class.java)
        } catch (ex: org.springframework.web.client.HttpStatusCodeException) {
            return org.springframework.http.ResponseEntity.status(ex.statusCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ex.responseBodyAsString ?: """{"error":"ACS error"}""")
        }
        com.dscorp.wispadmin.transport.InternalJson.validate(response)
        return response
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

    private fun base(): String = properties.acs.internalBaseUrl.trim().trimEnd('/')

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

    private fun exchangeRaw(
        path: String,
        method: HttpMethod,
        entity: HttpEntity<*>,
    ): org.springframework.http.ResponseEntity<String> {
        val root = base()
        if (root.isEmpty()) throw org.springframework.web.client.RestClientException("ACS URL is not configured")
        val response = try {
            restTemplate.exchange(java.net.URI.create("$root$path"), method, entity, String::class.java)
        } catch (ex: org.springframework.web.client.HttpStatusCodeException) {
            return org.springframework.http.ResponseEntity.status(ex.statusCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ex.responseBodyAsString ?: """{"error":"ACS error"}""")
        }
        com.dscorp.wispadmin.transport.InternalJson.validate(response)
        return response
    }

    companion object {
        const val HEADER = "X-Acs-Key"
    }
}
