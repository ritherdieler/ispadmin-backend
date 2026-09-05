package com.dscorp.wispadmin.wispadmin.trafficclient

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException
import javax.servlet.http.HttpServletRequest

@RestController
@ConditionalOnProperty(prefix = "traffic", name = ["client-enabled"], havingValue = "true")
class BandwidthIntelligenceFacadeController(
    private val trafficHttpClient: TrafficHttpClient,
) {
    @GetMapping("/traffic/bandwidth/v1/network")
    fun network(request: HttpServletRequest) = proxy("/api/traffic/v1/network", request.queryString)

    @GetMapping("/traffic/bandwidth/v1/overview")
    fun overview(request: HttpServletRequest) = proxy("/api/traffic/v1/overview", request.queryString)

    @GetMapping("/traffic/bandwidth/v1/series")
    fun series(request: HttpServletRequest) = proxy("/api/traffic/v1/series", request.queryString)

    @GetMapping("/traffic/bandwidth/v1/sources")
    fun sources() = proxy("/api/traffic/v1/sources", null)

    @GetMapping("/traffic/bandwidth/v1/anomalies")
    fun anomalies(request: HttpServletRequest) = proxy("/api/traffic/v1/anomalies", request.queryString)

    @GetMapping("/traffic/bandwidth/v1/subscriptions")
    fun subscriptions(request: HttpServletRequest) = proxy("/api/traffic/v1/subscriptions", request.queryString)

    @GetMapping("/traffic/bandwidth/v1/subscriptions/{id}")
    fun subscription(@PathVariable id: Int, request: HttpServletRequest) =
        proxy("/api/traffic/v1/subscriptions/$id", request.queryString)

    @GetMapping("/traffic/network/hourly-profile")
    fun hourly(request: HttpServletRequest) = proxy("/api/traffic/v1/network/hourly-profile", request.queryString)

    @GetMapping("/traffic/network/daily-trend")
    fun daily(request: HttpServletRequest) = proxy("/api/traffic/v1/network/daily-trend", request.queryString)

    @GetMapping("/traffic/network/insights")
    fun insights(request: HttpServletRequest) = proxy("/api/traffic/v1/network/insights", request.queryString)

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
