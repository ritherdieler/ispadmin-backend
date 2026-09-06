package com.dscorp.wispadmin.traffic.client

import com.dscorp.wispadmin.traffic.config.TrafficProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class HttpTrafficRouterDirectoryClient(
    private val properties: TrafficProperties,
    private val json: ObjectMapper,
    @Qualifier("trafficDirectoryRestTemplate") private val http: RestTemplate,
) {
    fun list(): List<TrafficRouterEntry> {
        val base=properties.coreBaseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "Core directory is not configured" }
        val result=mutableListOf<TrafficRouterEntry>()
        var page=0
        do {
            val headers=HttpHeaders().apply { set("X-Traffic-Key",properties.apiKey) }
            val body=http.exchange("$base/internal/traffic/routers?page=$page&size=200",HttpMethod.GET,HttpEntity<Void>(headers),String::class.java).body
                ?: error("Missing router directory")
            val node=json.readTree(body)
            require(node.path("items").isArray && node.path("totalPages").canConvertToInt()) { "Invalid router directory" }
            result.addAll(node.path("items").map { json.treeToValue(it,TrafficRouterEntry::class.java) })
            page++
            require(page<=10_000) { "Router pagination limit exceeded" }
        } while(page<node.path("totalPages").asInt())
        require(result.map { it.id }.distinct().size == result.size) { "Duplicate router identities" }
        return result
    }
}
class TrafficRouterEntry(val id: Int,val name: String,val host: String,val username: String,val password: String,val enabled: Boolean)
