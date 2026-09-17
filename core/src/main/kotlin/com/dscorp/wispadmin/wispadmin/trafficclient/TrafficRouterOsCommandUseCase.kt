package com.dscorp.wispadmin.wispadmin.trafficclient

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service

@Service
class TrafficRouterOsCommandUseCase(
    private val trafficHttpClient: TrafficHttpClient,
    private val objectMapper: ObjectMapper,
) {
    fun print(
        hostDeviceId: Int,
        path: String,
        query: Map<String, String> = emptyMap(),
        proplist: List<String> = emptyList(),
    ): Result<List<Map<String, String>>> = runCatching {
        val body = objectMapper.writeValueAsString(
            mapOf("path" to path, "query" to query, "proplist" to proplist),
        )
        parseRows(post("/api/traffic/v1/routeros/$hostDeviceId/print", body))
    }

    fun add(hostDeviceId: Int, path: String, args: Map<String, String>): Result<Unit> = runCatching {
        val body = objectMapper.writeValueAsString(mapOf("path" to path, "args" to args))
        post("/api/traffic/v1/routeros/$hostDeviceId/add", body)
        Unit
    }

    fun set(hostDeviceId: Int, path: String, id: String, args: Map<String, String>): Result<Unit> = runCatching {
        val body = objectMapper.writeValueAsString(mapOf("path" to path, "id" to id, "args" to args))
        post("/api/traffic/v1/routeros/$hostDeviceId/set", body)
        Unit
    }

    fun remove(hostDeviceId: Int, path: String, id: String): Result<Unit> = runCatching {
        val body = objectMapper.writeValueAsString(mapOf("path" to path, "id" to id))
        post("/api/traffic/v1/routeros/$hostDeviceId/remove", body)
        Unit
    }

    fun call(
        hostDeviceId: Int,
        path: String,
        args: Map<String, String> = emptyMap(),
    ): Result<List<Map<String, String>>> = runCatching {
        val body = objectMapper.writeValueAsString(mapOf("path" to path, "args" to args))
        parseRows(post("/api/traffic/v1/routeros/$hostDeviceId/call", body))
    }

    private fun post(path: String, body: String): String {
        return trafficHttpClient.postJson(path, body).body
            ?: throw IllegalStateException("Traffic RouterOS response body is empty")
    }

    private fun parseRows(body: String): List<Map<String, String>> {
        val root = objectMapper.readTree(body)
        val rows = root.path("rows")
        if (!rows.isArray) return emptyList()
        return rows.map { nodeToMap(it) }
    }

    private fun nodeToMap(node: JsonNode): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        node.fields().forEachRemaining { (key, value) ->
            result[key] = when {
                value.isNull -> ""
                value.isValueNode -> value.asText()
                else -> value.toString()
            }
        }
        return result
    }
}
