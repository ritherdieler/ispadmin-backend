package com.dscorp.wispadmin.netdiag.client

import com.dscorp.wispadmin.netdiag.port.NetDiagOltAlarm
import com.dscorp.wispadmin.netdiag.port.NetDiagOltAlarmParserPort
import com.dscorp.wispadmin.netdiag.port.NetDiagOltCliPort
import com.dscorp.wispadmin.netdiag.port.NetDiagOltDescriptor
import com.dscorp.wispadmin.netdiag.port.NetDiagOltDescriptorPort
import com.dscorp.wispadmin.netdiag.port.NetDiagOltInventoryPort
import com.dscorp.wispadmin.netdiag.port.NetDiagPonOnu
import com.dscorp.wispadmin.netdiag.port.OltCliOutcome
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

@Component
class NetDiagOltGatewayHttpClient(
    @Value("\${olt.gateway.internal-base-url:}") private val baseUrl: String,
    @Value("\${olt.gateway.api-key:}") private val apiKey: String,
) : NetDiagOltInventoryPort, NetDiagOltCliPort, NetDiagOltDescriptorPort, NetDiagOltAlarmParserPort {

    private val objectMapper = ObjectMapper()
    private val restTemplate = RestTemplate(SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(3_000)
        setReadTimeout(60_000)
    })

    override fun descriptor(): NetDiagOltDescriptor {
        val node = getJson(path("api", "olt-gateway", "descriptor")) ?: return NetDiagOltDescriptor(
            oltId = "unavailable",
            host = "",
            alarmPollEnabled = false,
            portsPerGponBoard = 16
        )
        return NetDiagOltDescriptor(
            oltId = node.path("oltId").asText("unavailable"),
            host = node.path("host").asText(""),
            alarmPollEnabled = node.path("alarmPollEnabled").asBoolean(false),
            portsPerGponBoard = node.path("portsPerGponBoard").asInt(16)
        )
    }

    override fun findOltId(name: String): Long? {
        val node = getJson(query("/api/olt-gateway/olts/id-by-name", mapOf("name" to name))) ?: return null
        return node.path("id").takeIf { it.isNumber }?.asLong()
    }

    override fun listOnusOnPon(oltName: String, board: Int, port: Int): List<NetDiagPonOnu> {
        val node = getJson(query("/api/olt-gateway/onus/pon", mapOf("oltName" to oltName, "board" to board, "port" to port))) ?: return emptyList()
        if (!node.isArray) return emptyList()
        return node.map { it.toPonOnu() }
    }

    override fun findOnu(oltName: String, board: Int, port: Int, onuIndex: Int): NetDiagPonOnu? {
        val node = getJson(query("/api/olt-gateway/onus/pon/one", mapOf("oltName" to oltName, "board" to board, "port" to port, "onuIndex" to onuIndex)))
            ?: return null
        if (node.isNull || node.isMissingNode) return null
        return node.toPonOnu()
    }

    override fun runAlarmPoll(): OltCliOutcome {
        val node = postJson(path("api", "olt-gateway", "admin", "alarms", "poll"), "{}") ?: return OltCliOutcome.Skipped("gateway_unavailable")
        val skipped = node.path("skippedReason").asText(null)?.takeIf { it.isNotBlank() && it != "null" }
        if (skipped != null) return OltCliOutcome.Skipped(skipped)
        val raw = node.path("raw").asText(null) ?: return OltCliOutcome.Skipped("empty_raw")
        return OltCliOutcome.Ok(raw)
    }

    override fun parseActiveAlarms(raw: String): List<NetDiagOltAlarm> {
        val escaped = objectMapper.writeValueAsString(mapOf("raw" to raw))
        val node = postJson(path("api", "olt-gateway", "admin", "alarms", "parse"), escaped) ?: return emptyList()
        if (!node.isArray) return emptyList()
        return node.map { it.toAlarm() }
    }

    private fun JsonNode.toPonOnu() = NetDiagPonOnu(
        onuIndex = path("onuIndex").asInt(0),
        sn = path("sn").asText(""),
        runState = path("runState").asText(null),
        lastDownCause = path("lastDownCause").asText(null),
        onuRxDbm = path("onuRxDbm").takeIf { it.isNumber }?.asDouble()
    )

    private fun JsonNode.toAlarm() = NetDiagOltAlarm(
        alarmIdHex = path("alarmIdHex").asText(null),
        alarmName = path("alarmName").asText(""),
        slotId = path("slotId").takeIf { it.isNumber }?.asInt(),
        portId = path("portId").takeIf { it.isNumber }?.asInt(),
        ontId = path("ontId").takeIf { it.isNumber }?.asInt(),
        reasonCode = path("reasonCode").asText(""),
        severity = path("severity").asText(""),
        component = path("component").asText(""),
        isClear = path("isClear").asBoolean(false),
        rawBlock = path("rawBlock").asText(""),
        unparsed = path("unparsed").asBoolean(false)
    )

    private fun query(path: String, params: Map<String, Any?>) =
        if (baseUrl.isBlank()) java.net.URI.create("http://127.0.0.1/") else com.dscorp.wispadmin.transport.InternalUris.uri(baseUrl, path, params)
    private fun path(vararg segments: String) =
        if (baseUrl.isBlank()) java.net.URI.create("http://127.0.0.1/") else com.dscorp.wispadmin.transport.InternalUris.path(baseUrl, *segments)

    private fun getJson(uri: java.net.URI): JsonNode? {
        if (baseUrl.isBlank()) return null
        return try {
            val headers = HttpHeaders()
            headers.set("X-Olt-Gateway-Key", apiKey)
            val response = restTemplate.exchange(uri, HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
            response.body?.let { objectMapper.readTree(it) }
        } catch (ex: Exception) {
            logger.warn("NetDiag OLT HTTP failed for {}: {}", uri, ex.message)
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
            response.body?.let { objectMapper.readTree(it) }
        } catch (ex: Exception) {
            logger.warn("NetDiag OLT HTTP POST failed for {}: {}", uri, ex.message)
            null
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(NetDiagOltGatewayHttpClient::class.java)
    }
}
