package com.dscorp.wispadmin.routeros.adapter

import com.dscorp.wispadmin.routeros.port.MikrotikCommandException
import com.dscorp.wispadmin.routeros.port.MikrotikDeviceRef
import com.dscorp.wispadmin.routeros.port.MikrotikSession
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException

class RouterOs7RestSession(
    private val device: MikrotikDeviceRef,
    private val scheme: String,
    private val httpClient: OkHttpClient,
    private val objectMapper: ObjectMapper
) : MikrotikSession {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun print(path: String, query: Map<String, String>): List<Map<String, String>> {
        val bodyNode = objectMapper.createObjectNode()
        if (query.isNotEmpty()) {
            val queryStack = objectMapper.createArrayNode()
            query.forEach { (key, value) ->
                queryStack.add("$key=$value")
            }
            bodyNode.set<com.fasterxml.jackson.databind.node.ArrayNode>(".query", queryStack)
        }
        val responseBody = execute("POST", RouterOsRestPathMapper.printPath(path), bodyNode.toString())
        return parseRows(responseBody)
    }

    override fun call(path: String, args: Map<String, String>): List<Map<String, String>> {
        val body = objectMapper.writeValueAsString(args)
        val responseBody = execute("POST", RouterOsRestPathMapper.resourcePath(path), body)
        return parseRows(responseBody)
    }

    override fun add(path: String, args: Map<String, String>) {
        val body = objectMapper.writeValueAsString(args)
        execute("PUT", RouterOsRestPathMapper.resourcePath(path), body)
    }

    override fun set(path: String, id: String, args: Map<String, String>) {
        val body = objectMapper.writeValueAsString(args)
        execute("PATCH", RouterOsRestPathMapper.resourcePath(path, id), body)
    }

    override fun remove(path: String, id: String) {
        execute("DELETE", RouterOsRestPathMapper.resourcePath(path, id), null)
    }

    override fun execute(command: String): List<Map<String, String>> {
        throw MikrotikCommandException(
            "Raw execute is not supported by REST adapter; use print/add/set/remove: $command"
        )
    }

    private fun execute(method: String, path: String, jsonBody: String?): String {
        val url = "$scheme://${device.host}:${device.port}$path"
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(device.username, device.password))
            .header("Accept", "application/json")
        when (method) {
            "POST", "PUT", "PATCH" -> {
                val payload = jsonBody ?: "{}"
                builder.method(method, payload.toRequestBody(jsonMediaType))
            }
            "DELETE" -> builder.delete()
            else -> builder.get()
        }
        try {
            httpClient.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw MikrotikExceptionMapper.mapHttp(
                        response.code,
                        body,
                        "rest $method $path"
                    )
                }
                return body
            }
        } catch (error: SocketTimeoutException) {
            throw MikrotikExceptionMapper.map(error, "rest $method $path")
        } catch (error: IOException) {
            throw MikrotikExceptionMapper.map(error, "rest $method $path")
        } catch (error: Exception) {
            throw MikrotikExceptionMapper.map(error, "rest $method $path")
        }
    }

    private fun parseRows(body: String): List<Map<String, String>> {
        if (body.isBlank()) {
            return emptyList()
        }
        val root = objectMapper.readTree(body)
        return when {
            root.isArray -> root.map { nodeToMap(it) }
            root.isObject -> listOf(nodeToMap(root))
            else -> emptyList()
        }
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
