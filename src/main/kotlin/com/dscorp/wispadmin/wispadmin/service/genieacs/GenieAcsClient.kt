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
) {
    fun toErrorDetail(): String = when (statusCode) {
        202 -> toResponseMessage()
        else -> GenieAcsClient.formatTaskError(this)
    }

    fun toResponseMessage(): String = GenieAcsCurlLogger.formatResponse(statusCode, body)
}

data class GenieAcsQueuePurgeResult(
    val tasksDeleted: Int,
    val faultsDeleted: Int,
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

    fun addObject(
        deviceId: String,
        objectName: String,
        connectionRequest: Boolean,
    ): GenieAcsTaskResult {
        val payload = mapOf(
            "name" to "addObject",
            "objectName" to objectName,
        )
        return postTask(deviceId, payload, connectionRequest)
    }

    /**
     * Removes pending tasks and faults for [deviceId] so a new SPV/GPV session is not blocked
     * by stale lab retries (GenieACS replays queued tasks on every connection request).
     */
    fun purgeDeviceQueue(deviceId: String): GenieAcsQueuePurgeResult {
        val tasksDeleted = deleteResourcesForDevice("/tasks/", deviceId)
        val faultsDeleted = deleteResourcesForDevice("/faults/", deviceId)
        if (tasksDeleted > 0 || faultsDeleted > 0) {
            log.info(
                "Cola GenieACS purgada para {}: {} tasks, {} faults",
                deviceId,
                tasksDeleted,
                faultsDeleted,
            )
        }
        return GenieAcsQueuePurgeResult(
            tasksDeleted = tasksDeleted,
            faultsDeleted = faultsDeleted,
        )
    }

    fun findFaultBodyForTask(deviceId: String, taskId: String): String? {
        val queryJson = objectMapper.writeValueAsString(mapOf("device" to deviceId))
        val uri = UriComponentsBuilder
            .fromHttpUrl(properties.nbiBaseUrl.trimEnd('/'))
            .path("/faults/")
            .queryParam("query", queryJson)
            .build()
            .encode()
            .toUri()
        val body = try {
            restTemplate.getForObject(uri, String::class.java)
        } catch (ex: Exception) {
            log.warn("No se pudo consultar faults de {}: {}", deviceId, ex.message)
            return null
        } ?: return null
        val root = objectMapper.readTree(body)
        if (!root.isArray) return null
        val channel = "task_$taskId"
        return root.firstOrNull { node ->
            node.path("channel").asText(null) == channel
        }?.let { objectMapper.writeValueAsString(it) }
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
    fun listWanConnectionIndices(
        deviceId: String,
        wcdParentPath: String = DEFAULT_WCD_PARENT,
    ): List<Int> {
        val wanConn = readWanConnectionDeviceNode(deviceId, wcdParentPath) ?: return emptyList()
        return wanConn.fieldNames().asSequence()
            .mapNotNull { it.toIntOrNull() }
            .filter { index -> hasWanIpOnNode(wanConn, index) }
            .sorted()
            .toList()
    }

    /**
     * Returns every WANConnectionDevice instance index present in the ACS cache,
     * including slots that still have no WANIPConnection.
     */
    fun listWanConnectionDeviceIndices(
        deviceId: String,
        wcdParentPath: String = DEFAULT_WCD_PARENT,
    ): List<Int> {
        val wanConn = readWanConnectionDeviceNode(deviceId, wcdParentPath) ?: return emptyList()
        return wanConn.fieldNames().asSequence()
            .mapNotNull { it.toIntOrNull() }
            .sorted()
            .toList()
    }

    fun hasWanIpConnection(
        deviceId: String,
        wanIndex: Int,
        wcdParentPath: String = DEFAULT_WCD_PARENT,
        wanIpInstanceIndex: Int = 1,
    ): Boolean {
        val wanConn = readWanConnectionDeviceNode(deviceId, wcdParentPath) ?: return false
        return hasWanIpOnNode(wanConn, wanIndex, wanIpInstanceIndex)
    }

    private fun hasWanIpOnNode(
        wanConn: JsonNode,
        wanIndex: Int,
        wanIpInstanceIndex: Int = 1,
    ): Boolean {
        val instanceKey = wanIpInstanceIndex.toString()
        val wanIp = wanConn.path(wanIndex.toString()).path("WANIPConnection")
        return wanIp.path(instanceKey).isObject || wanIp.has(instanceKey)
    }

    private fun readWanConnectionDeviceNode(
        deviceId: String,
        wcdParentPath: String = DEFAULT_WCD_PARENT,
    ): JsonNode? {
        val uri = deviceUri(deviceId, wcdParentPath)
        val body = try {
            restTemplate.getForObject(uri, String::class.java)
        } catch (ex: Exception) {
            log.warn("No se pudo listar WANConnectionDevice de {}: {}", deviceId, ex.message)
            return null
        } ?: return null
        val root = objectMapper.readTree(body)
        val deviceNode = when {
            root.isArray && root.size() > 0 -> root[0]
            root.isObject -> root
            else -> return null
        }
        var node = deviceNode
        for (segment in wcdParentPath.split('.')) {
            node = node.path(segment)
        }
        return node.takeIf { it.isObject }
    }

    private fun postTask(
        deviceId: String,
        payload: Map<String, Any>,
        connectionRequest: Boolean,
    ): GenieAcsTaskResult {
        val uri = taskUri(deviceId, connectionRequest)
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val jsonBody = objectMapper.writeValueAsString(payload)
        logCurlPostTask(uri, jsonBody)
        val entity = HttpEntity(jsonBody, headers)
        return try {
            val response = restTemplate.exchange(uri, HttpMethod.POST, entity, String::class.java)
            val body = response.body
            buildTaskResult(
                statusCode = response.statusCodeValue,
                body = body,
                accepted = isAcceptedTaskResponse(response.statusCodeValue, body),
            )
        } catch (ex: HttpStatusCodeException) {
            val body = ex.responseBodyAsString
            buildTaskResult(
                statusCode = ex.rawStatusCode,
                body = body,
                accepted = false,
            )
        }
    }

    private fun isAcceptedTaskResponse(statusCode: Int, body: String?): Boolean {
        if (statusCode !in 200..299) return false
        if (statusCode == 202) return !indicatesImmediateTaskFailure(body)
        return true
    }

    private fun indicatesImmediateTaskFailure(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        if (body.contains(CR_CREDENTIALS_ERROR, ignoreCase = true)) return true
        if (extractErrorDetail(body) != null && !looksLikeQueuedTask(body)) return true
        return false
    }

    private fun looksLikeQueuedTask(body: String): Boolean {
        return try {
            val root = objectMapper.readTree(body)
            root.has("_id") && root.path("fault").isMissingNode && root.path("error").isMissingNode
        } catch (_: Exception) {
            false
        }
    }

    private fun buildTaskResult(
        statusCode: Int,
        body: String?,
        accepted: Boolean,
    ): GenieAcsTaskResult {
        logGenieAcsResponse(statusCode, body)
        return GenieAcsTaskResult(
            statusCode = statusCode,
            body = body,
            accepted = accepted,
            connectionRequestFailed = body?.contains(CR_CREDENTIALS_ERROR, ignoreCase = true) == true,
            taskId = parseTaskId(body),
        )
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

    private fun deleteResourcesForDevice(collectionPath: String, deviceId: String): Int {
        val ids = listResourceIds(collectionPath, deviceId)
        var deleted = 0
        for (id in ids) {
            if (deleteResource(collectionPath, id)) {
                deleted++
            }
        }
        return deleted
    }

    private fun listResourceIds(collectionPath: String, deviceId: String): List<String> {
        val queryJson = objectMapper.writeValueAsString(mapOf("device" to deviceId))
        val uri = UriComponentsBuilder
            .fromHttpUrl(properties.nbiBaseUrl.trimEnd('/'))
            .path(collectionPath)
            .queryParam("query", queryJson)
            .build()
            .encode()
            .toUri()
        val body = try {
            restTemplate.getForObject(uri, String::class.java)
        } catch (ex: Exception) {
            log.warn(
                "No se pudo listar {} para {}: {}",
                collectionPath.trim('/'),
                deviceId,
                ex.message,
            )
            return emptyList()
        } ?: return emptyList()
        val root = objectMapper.readTree(body)
        if (!root.isArray) return emptyList()
        return root.mapNotNull { node ->
            node.path("_id").asText(null)?.takeIf { it.isNotBlank() }
        }
    }

    private fun deleteResource(collectionPath: String, resourceId: String): Boolean {
        val encodedId = URLEncoder.encode(resourceId, StandardCharsets.UTF_8).replace("+", "%20")
        val uri = UriComponentsBuilder
            .fromHttpUrl(properties.nbiBaseUrl.trimEnd('/'))
            .path("${collectionPath.trimEnd('/')}/$encodedId")
            .build(true)
            .toUri()
        return try {
            restTemplate.exchange(uri, HttpMethod.DELETE, HttpEntity.EMPTY, String::class.java)
            true
        } catch (ex: Exception) {
            log.warn(
                "No se pudo eliminar {} {}: {}",
                collectionPath.trim('/'),
                resourceId,
                ex.message,
            )
            false
        }
    }

    private fun logCurlPostTask(uri: URI, jsonBody: String) {
        if (!properties.logCurl) return
        log.info("[GenieACS curl]\n{}", GenieAcsCurlLogger.formatPostTask(uri, jsonBody))
    }

    private fun logGenieAcsResponse(statusCode: Int, body: String?) {
        if (!properties.logCurl) return
        log.info("[GenieACS response]\n{}", GenieAcsCurlLogger.formatResponse(statusCode, body))
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
        const val DEFAULT_WCD_PARENT = "InternetGatewayDevice.WANDevice.1.WANConnectionDevice"
        const val CR_CREDENTIALS_ERROR = "Incorrect connection request credentials"
        const val DEFAULT_DEVICE_PROJECTION =
            "_id,_lastInform,_lastBoot,_deviceId,InternetGatewayDevice.ManagementServer.ConnectionRequestURL"

        fun formatTaskError(result: GenieAcsTaskResult): String {
            val detail = extractErrorDetail(result.body)
            return buildString {
                append("GenieACS HTTP ").append(result.statusCode)
                when {
                    !detail.isNullOrBlank() -> append(": ").append(detail)
                    !result.body.isNullOrBlank() -> append(": ").append(result.body!!.trim().take(200))
                }
            }
        }

        fun extractErrorDetail(body: String?): String? {
            if (body.isNullOrBlank()) return null
            return try {
                val root = ObjectMapper().readTree(body)
                formatFaultDetail(root)?.let { return it }
                sequenceOf("detail", "message", "fault", "error")
                    .map { root.path(it) }
                    .firstOrNull { node ->
                        !node.isMissingNode && !node.isNull && node.isValueNode && node.asText("").isNotBlank()
                    }
                    ?.asText()
            } catch (_: Exception) {
                body.trim().take(200).ifBlank { null }
            }
        }

        private fun formatFaultDetail(root: JsonNode): String? {
            val code = root.path("code").asText(null)
            val message = root.path("message").asText(null)
            val detail = root.path("detail")
            if (detail.isMissingNode || detail.isNull) {
                return listOfNotNull(code, message).joinToString(": ").ifBlank { null }
            }
            val spvFaults = detail.path("setParameterValuesFault")
            if (spvFaults.isArray && spvFaults.size() > 0) {
                return spvFaults.joinToString("; ") { fault ->
                    val param = fault.path("parameterName").asText("?")
                        .substringAfterLast('.')
                    val faultString = fault.path("faultString").asText("error")
                    val faultCode = fault.path("faultCode").asText(null)
                    buildString {
                        append(param).append(": ").append(faultString)
                        if (!faultCode.isNullOrBlank()) append(" (").append(faultCode).append(')')
                    }
                }
            }
            if (detail.isObject) {
                val faultString = detail.path("faultString").asText(null)
                if (!faultString.isNullOrBlank()) return faultString
            }
            if (detail.isValueNode) return detail.asText(null)
            return listOfNotNull(code, message).joinToString(": ").ifBlank { null }
        }

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
