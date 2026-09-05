package com.dscorp.wispadmin.wispadmin.trafficclient

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/subscription")
@ConditionalOnProperty(prefix = "traffic", name = ["client-enabled"], havingValue = "true")
class SubscriptionTrafficFacadeController(
    private val trafficHttpClient: TrafficHttpClient,
) {
    @GetMapping("/{id}/traffic")
    fun series(@PathVariable id: Int, request: HttpServletRequest): ResponseEntity<String> =
        proxy("/api/traffic/v1/by-subscription/$id/series", request.queryString)

    @GetMapping("/{id}/traffic/latest")
    fun latest(@PathVariable id: Int): ResponseEntity<String> =
        proxy("/api/traffic/v1/by-subscription/$id/latest", null)

    @GetMapping("/{id}/traffic/summary")
    fun summary(@PathVariable id: Int, request: HttpServletRequest): ResponseEntity<String> =
        proxy("/api/traffic/v1/by-subscription/$id/summary", request.queryString)

    @GetMapping("/{id}/traffic/today")
    fun today(@PathVariable id: Int): ResponseEntity<String> =
        proxy("/api/traffic/v1/by-subscription/$id/today", null)

    @GetMapping("/{id}/traffic/day")
    fun day(@PathVariable id: Int, request: HttpServletRequest): ResponseEntity<String> =
        proxy("/api/traffic/v1/by-subscription/$id/day", request.queryString)

    private fun proxy(path: String, query: String?): ResponseEntity<String> {
        return try {
            val upstream = trafficHttpClient.getJson(path, query)
            ResponseEntity.status(upstream.statusCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(upstream.body ?: "{}")
        } catch (ex: RestClientException) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "traffic subsystem unavailable")
        } catch (ex: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "traffic subsystem unavailable")
        }
    }
}
