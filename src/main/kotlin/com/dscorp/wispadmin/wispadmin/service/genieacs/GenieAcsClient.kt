package com.dscorp.wispadmin.wispadmin.service.genieacs

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class GenieAcsTaskResult(
    val statusCode: Int,
    val body: String?,
    val accepted: Boolean,
    val connectionRequestFailed: Boolean = false,
    val taskId: String? = null,
)

@Component
class GenieAcsClient(
    private val properties: GenieAcsProperties,
    private val objectMapper: ObjectMapper,
    @Qualifier("genieAcsRestTemplate") private val restTemplate: RestTemplate,
) {
    private val log = LoggerFactory.getLogger(GenieAcsClient::class.java)

    fun listDevices(
        projection: String = DEFAULT_DEVICE_PROJECTION,
    ): List<GenieAcsDevice> {
        val uri = UriComponentsBuilder
            .fromHttpUrl(properties.nbiBaseUrl.trimEnd('/'))
            .path("/devices/")
            .queryParam("projection", projection)
            .build(true)
            .toUri()
        val body = restTemplate.getForObject(uri, String::class.java) ?: return emptyList()
        val root = objectMapper.readTree(body)
        if (!root.isArray) return emptyList()
        return root.mapNotNull { parseDevice(it) }
    }

    fun findDeviceBySerialSuffix(suffix: String): List<GenieAcsDevice> {
        val normalized = suffix.uppercase()
        return listDevices().filter { device ->
            Tr069SerialMatcher.normalizeSuffix(device.serialNumber) == normalized ||
                device.id.uppercase().endsWith(normalized)
        }
    }

    fun setParameterValues(
        deviceId: String,
        values: List<Tr069ParameterValue>,
        connectionRequest: Boolean,
    ): GenieAcsTaskResult {
        val payload = mapOf(
            "name" to "setParameterValues",
            "parameterValues" to values.map { listOf(it.path, it.value, it.type) },
        )
        return postTask(deviceId, payload, connectionRequest)
    }

    fun getParameterValues(
        deviceId: String,
        parameterNames: List<String>,
        connectionRequest: Boolean = false,
    ): GenieAcsTaskResult {
        val payload = mapOf(
            "name" to "getParameterValues",
            "parameterNames" to parameterNames,
        )
        return postTask(deviceId, payload, connectionRequest)
    }

    fun reboot(
        deviceId: String,
        connectionRequest: Boolean = true,
    ): GenieAcsTaskResult {
        return postTask(deviceId, mapOf("name" to "reboot"), connectionRequest)
    }

    fun getDeviceParameterValue(deviceId: String, dottedPath: String): String? {
        val projection = dottedPath
        val uri = deviceUri(deviceId, projection)
        val body = try {
            restTemplate.getForObject(uri, String::class.java)
        } catch (ex: Exception) {
            log.warn("No se pudo leer parámetro {} de {}: {}", dottedPath, deviceId, ex.message)
            return null
        } ?: return null
        val root = objectMapper.readTree(body)
        val deviceNode = when {
            root.isArray && root.size() > 0 -> root[0]
            root.isObject -> root
            else -> return null
        }
        return readNestedValue(deviceNode, dottedPath)
    }

    /**
     * Returns WANConnectionDevice instance indices that expose WANIPConnection.1.
     * Empty when GenieACS has not reported the WAN tree yet.
     */
    fun listWanConnectionIndices(deviceId: String): List<Int> {
        val projection = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice"
        val uri = deviceUri(deviceId, projection)
        val body = try {
            restTemplate.getForObject(uri, String::class.java)
        } catch (ex: Exception) {
            log.warn("No se pudo listar WANConnectionDevice de {}: {}", deviceId, ex.message)
            return emptyList()
        } ?: return emptyList()
        val root = objectMapper.readTree(body)
        val deviceNode = when {
            root.isArray && root.size() > 0 -> root[0]
            root.isObject -> root
            else -> return emptyList()
        }
        val wanConn = deviceNode
            .path("InternetGatewayDevice")
            .path("WANDevice")
            .path("1")
            .path("WANConnectionDevice")
        if (!wanConn.isObject) return emptyList()
        return wanConn.fieldNames().asSequence()
            .mapNotNull { it.toIntOrNull() }
            .filter { index ->
                wanConn.path(index.toString()).path("WANIPConnection").path("1").isObject ||
                    wanConn.path(index.toString()).path("WANIPConnection").has("1")
            }
            .sorted()
            .toList()
    }

    private fun postTask(
        deviceId: String,
        payload: Map<String, Any>,
        connectionRequest: Boolean,
    ): GenieAcsTaskResult {
        val uri = taskUri(deviceId, connectionRequest)
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val entity = HttpEntity(objectMapper.writeValueAsString(payload), headers)
        return try {
            val response = restTemplate.exchange(uri, HttpMethod.POST, entity, String::class.java)
            val body = response.body
            GenieAcsTaskResult(
                statusCode = response.statusCodeValue,
                body = body,
                accepted = response.statusCodeValue in 200..299,
                connectionRequestFailed = body?.contains(CR_CREDENTIALS_ERROR, ignoreCase = true) == true,
                taskId = parseTaskId(body),
            )
        } catch (ex: HttpStatusCodeException) {
            val body = ex.responseBodyAsString
            GenieAcsTaskResult(
                statusCode = ex.rawStatusCode,
                body = body,
                accepted = false,
                connectionRequestFailed = body.contains(CR_CREDENTIALS_ERROR, ignoreCase = true),
                taskId = parseTaskId(body),
            )
        }
    }

    private fun taskUri(deviceId: String, connectionRequest: Boolean): URI {
        val encodedId = URLEncoder.encode(deviceId, StandardCharsets.UTF_8).replace("+", "%20")
        val builder = UriComponentsBuilder
            .fromHttpUrl(properties.nbiBaseUrl.trimEnd('/'))
            .path("/devices/$encodedId/tasks")
        if (connectionRequest) {
            builder.query("connection_request")
        }
        return builder.build(true).toUri()
    }

    private fun deviceUri(deviceId: String, projection: String): URI {
        val queryJson = objectMapper.writeValueAsString(mapOf("_id" to deviceId))
        return UriComponentsBuilder
            .fromHttpUrl(properties.nbiBaseUrl.trimEnd('/'))
            .path("/devices/")
            .queryParam("query", queryJson)
            .queryParam("projection", projection)
            .build()
            .encode()
            .toUri()
    }

    private fun parseDevice(node: JsonNode): GenieAcsDevice? {
        val id = node.path("_id").asText(null) ?: return null
        val deviceId = node.path("_deviceId")
        return GenieAcsDevice(
            id = id,
            serialNumber = deviceId.path("_SerialNumber").asText(null)
                ?: deviceId.path("SerialNumber").asText(null),
            productClass = deviceId.path("_ProductClass").asText(null)
                ?: deviceId.path("ProductClass").asText(null),
            lastInform = node.path("_lastInform").asText(null),
            manufacturer = deviceId.path("_Manufacturer").asText(null)
                ?: deviceId.path("Manufacturer").asText(null),
            oui = deviceId.path("_OUI").asText(null)
                ?: deviceId.path("OUI").asText(null),
            softwareVersion = deviceId.path("_SoftwareVersion").asText(null)
                ?: deviceId.path("SoftwareVersion").asText(null),
            hardwareVersion = deviceId.path("_HardwareVersion").asText(null)
                ?: deviceId.path("HardwareVersion").asText(null),
            lastBoot = node.path("_lastBoot").asText(null),
            connectionRequestUrl = readNestedValue(
                node,
                "InternetGatewayDevice.ManagementServer.ConnectionRequestURL",
            ),
        )
    }

    private fun parseTaskId(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val root = objectMapper.readTree(body)
            root.path("_id").asText(null)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val CR_CREDENTIALS_ERROR = "Incorrect connection request credentials"
        const val DEFAULT_DEVICE_PROJECTION =
            "_id,_lastInform,_lastBoot,_deviceId,InternetGatewayDevice.ManagementServer.ConnectionRequestURL"

        fun readNestedValue(root: JsonNode, dottedPath: String): String? {
            var current: JsonNode = root
            for (part in dottedPath.split('.')) {
                current = current.get(part) ?: return null
            }
            return when {
                current.has("_value") -> current.get("_value").asText(null)
                current.isValueNode -> current.asText(null)
                else -> null
            }
        }
    }
}
