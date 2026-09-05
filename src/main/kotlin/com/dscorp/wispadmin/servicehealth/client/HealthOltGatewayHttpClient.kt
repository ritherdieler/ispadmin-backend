package com.dscorp.wispadmin.servicehealth.client

import com.dscorp.wispadmin.servicehealth.port.HealthCpeCommand
import com.dscorp.wispadmin.servicehealth.port.HealthCpePort
import com.dscorp.wispadmin.servicehealth.port.HealthCpeTelemetry
import com.dscorp.wispadmin.servicehealth.port.HealthLabOpticalPort
import com.dscorp.wispadmin.servicehealth.port.HealthLabOpticalRefresh
import com.dscorp.wispadmin.servicehealth.port.HealthOnuPort
import com.dscorp.wispadmin.servicehealth.port.HealthOnuRef
import com.dscorp.wispadmin.servicehealth.port.HealthOpticalObservation
import com.dscorp.wispadmin.servicehealth.port.HealthOpticalRow
import com.dscorp.wispadmin.wispadmin.repository.SubscriptionRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
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
    private val subscriptions: ObjectProvider<SubscriptionRepository>,
) : HealthOnuPort, HealthLabOpticalPort, HealthCpePort {

    private val objectMapper = ObjectMapper()
    private val restTemplate = RestTemplate(SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(3_000)
        setReadTimeout(30_000)
    })

    override fun findBySn(sn: String): HealthOnuRef? = getRef("/api/olt-gateway/health-onus/by-sn?sn=$sn")

    override fun findByExternalId(externalId: String): HealthOnuRef? =
        getRef("/api/olt-gateway/health-onus/by-external-id?id=$externalId")

    override fun findByOltBoardPortOnu(oltId: Long, board: Int, port: Int, onuIndex: Int): HealthOnuRef? =
        getRef("/api/olt-gateway/health-onus/by-position?oltId=$oltId&board=$board&port=$port&onuIndex=$onuIndex")

    override fun findByOlt(oltId: Long): List<HealthOnuRef> {
        val node = getJson("/api/olt-gateway/health-onus/by-olt/$oltId") ?: return emptyList()
        if (!node.isArray) return emptyList()
        return node.mapNotNull { it.toRef() }
    }

    override fun findOltIdByName(name: String): Long? {
        val node = getJson("/api/olt-gateway/olts/id-by-name?name=$name") ?: return null
        return node.path("id").takeIf { it.isNumber }?.asLong()
    }

    override fun refreshSubscription(subscriptionId: Int): HealthLabOpticalRefresh {
        val sn = subscriptions.ifAvailable?.findById(subscriptionId)?.orElse(null)?.fiberOnu?.sn
            ?: return HealthLabOpticalRefresh(false, error = "missing_sn")
        val node = postJson("/api/olt-gateway/admin/lab-optical/refresh", """{"sn":"$sn"}""")
            ?: return HealthLabOpticalRefresh(false, error = "gateway_unavailable")
        return HealthLabOpticalRefresh(
            collected = node.path("collected").asBoolean(false),
            unmapped = node.path("unmapped").asBoolean(false),
            error = node.path("error").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    override fun telemetry(sn: String): HealthCpeTelemetry? {
        val node = getJson("/api/olt-gateway/onus/$sn/cpe/telemetry") ?: return null
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
        )
    }

    override fun reboot(sn: String): HealthCpeCommand = command("/api/olt-gateway/onus/$sn/cpe/reboot")

    override fun wifiRefresh(sn: String): HealthCpeCommand = command("/api/olt-gateway/onus/$sn/cpe/wifi-refresh")

    private fun command(path: String): HealthCpeCommand {
        val node = postJson(path, "{}")
            ?: return HealthCpeCommand(false, "FAILED", "gateway_unavailable")
        return HealthCpeCommand(
            accepted = node.path("accepted").asBoolean(false),
            status = node.path("status").asText("FAILED"),
            message = node.path("message").asText(null)?.takeIf { it.isNotBlank() && it != "null" },
        )
    }

    fun pullOptical(): List<HealthOpticalObservation> {
        val node = getJson("/api/olt-gateway/onus/configured?size=200") ?: return emptyList()
        val items = node.path("items")
        if (!items.isArray) return emptyList()
        val observedAt = Instant.now()
        return items.groupBy { it.path("oltId").takeIf { n -> n.isNumber }?.asLong() }
            .mapNotNull { (oltId, rows) ->
                if (oltId == null) return@mapNotNull null
                HealthOpticalObservation(
                    oltId = oltId,
                    observedAt = observedAt,
                    rows = rows.map { item ->
                        HealthOpticalRow(
                            slot = item.path("board").asInt(0),
                            port = item.path("port").asInt(0),
                            ontId = item.path("onuIndex").asInt(0),
                            rxPowerDbm = item.path("onuRxDbm").takeIf { it.isNumber }?.asDouble(),
                            txPowerDbm = item.path("onuTxDbm").takeIf { it.isNumber }?.asDouble(),
                            oltRxPowerDbm = item.path("oltRxDbm").takeIf { it.isNumber }?.asDouble(),
                            temperatureC = null,
                            biasCurrentMa = null,
                            distanceM = null,
                        )
                    },
                )
            }
    }

    fun pullStates(): List<Triple<String, String?, String?>> {
        val node = getJson("/api/olt-gateway/onus/configured?size=200") ?: return emptyList()
        val items = node.path("items")
        if (!items.isArray) return emptyList()
        return items.mapNotNull { item ->
            val sn = item.path("sn").asText(null)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Triple(sn, item.path("runState").asText(null), item.path("lastDownCause").asText(null))
        }
    }

    private fun getRef(path: String): HealthOnuRef? = getJson(path)?.toRef()

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

    private fun getJson(path: String): JsonNode? {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return null
        return try {
            val headers = HttpHeaders()
            headers.set("X-Olt-Gateway-Key", apiKey)
            val response = restTemplate.exchange(
                "$base$path",
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                String::class.java,
            )
            val body = response.body ?: return null
            objectMapper.readTree(body)
        } catch (ex: Exception) {
            logger.warn("Health OLT HTTP failed for {}: {}", path, ex.message)
            null
        }
    }

    private fun postJson(path: String, json: String): JsonNode? {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return null
        return try {
            val headers = HttpHeaders()
            headers.set("X-Olt-Gateway-Key", apiKey)
            headers.set("Content-Type", "application/json")
            val response = restTemplate.exchange(
                "$base$path",
                HttpMethod.POST,
                HttpEntity(json, headers),
                String::class.java,
            )
            val body = response.body ?: return null
            objectMapper.readTree(body)
        } catch (ex: Exception) {
            logger.warn("Health OLT HTTP POST failed for {}: {}", path, ex.message)
            null
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(HealthOltGatewayHttpClient::class.java)
    }
}
