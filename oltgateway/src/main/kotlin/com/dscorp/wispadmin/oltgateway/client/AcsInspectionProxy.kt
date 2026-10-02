package com.dscorp.wispadmin.oltgateway.client

import com.dscorp.wispadmin.oltgateway.config.OltGatewayProperties
import com.dscorp.wispadmin.transport.InternalJson
import com.dscorp.wispadmin.transport.InvalidSubsystemResponse
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

class AcsInspectionProxy(
    private val properties: OltGatewayProperties,
    private val http: RestTemplate,
    private val json: ObjectMapper,
) {
    private val router = AcsCallerRouter(properties)

    fun summary(sn: String, env: String?): JsonNode = fetch(sn, listOf("summary"), env)

    fun tree(sn: String, parent: String?, query: String?, env: String?): JsonNode =
        fetch(sn, listOf("tree"), env, mapOf("parent" to parent, "q" to query))

    fun currentFaults(sn: String, env: String?): JsonNode = fetch(sn, listOf("faults", "current"), env)

    fun faultHistory(sn: String, page: Int, size: Int, env: String?): JsonNode =
        fetch(sn, listOf("faults", "history"), env, mapOf("page" to page, "size" to size))

    private fun fetch(sn: String, suffix: List<String>, env: String?, params: Map<String, Any?> = emptyMap()): JsonNode {
        val builder = UriComponentsBuilder.fromUriString(router.baseUrl(env))
            .pathSegment("api", "acs", "v1", "cpe", sn, "inspection", *suffix.toTypedArray())
        params.forEach { (key, value) -> if (value != null) builder.queryParam(key, value) }
        val headers = HttpHeaders().apply { set(HttpAcsCpeClient.HEADER, properties.acs.apiKey) }
        val response = http.exchange(builder.build().encode().toUri(), HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
        InternalJson.validate(response)
        val node = json.readTree(response.body)
        if (node == null || !node.isObject) throw InvalidSubsystemResponse()
        return node
    }
}
