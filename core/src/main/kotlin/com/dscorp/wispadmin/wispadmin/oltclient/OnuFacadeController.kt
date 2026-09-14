package com.dscorp.wispadmin.wispadmin.oltclient

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/onu")
@ConditionalOnProperty(prefix = "olt.gateway", name = ["client-enabled"], havingValue = "true")
class OnuFacadeController(
    private val oltGatewayHttpClient: OltGatewayHttpClient,
) {
    @GetMapping("/configured")
    fun listConfigured(request: HttpServletRequest): ResponseEntity<String> =
        proxy("/api/olt-gateway/onus/configured", forwardedQuery(request))

    @GetMapping("/configured/{externalId}")
    fun getConfigured(@PathVariable externalId: String): ResponseEntity<String> =
        proxy("/api/olt-gateway/onus/configured/$externalId", null)

    @GetMapping("/configured/{externalId}/status")
    fun getConfiguredStatus(@PathVariable externalId: String): ResponseEntity<String> =
        proxy("/api/olt-gateway/onus/configured/$externalId/status", null)

    @GetMapping("/configured/{externalId}/history")
    fun getConfiguredHistory(@PathVariable externalId: String, request: HttpServletRequest): ResponseEntity<String> =
        proxy("/api/olt-gateway/onus/configured/$externalId/history", forwardedQuery(request))

    @GetMapping("/catalog")
    fun listCatalogs(): ResponseEntity<String> =
        proxy("/api/olt-gateway/onus/catalog", null)

    @GetMapping("/catalog/boards-ports")
    fun listBoardsPorts(request: HttpServletRequest): ResponseEntity<String> =
        proxy("/api/olt-gateway/onus/catalog/boards-ports", forwardedQuery(request))

    @PostMapping("/sync/inventory")
    fun syncInventory(): ResponseEntity<String> = post("/api/olt-gateway/admin/sync/snmp-inventory")

    @PostMapping("/sync/signal")
    fun syncSignal(): ResponseEntity<String> = post("/api/olt-gateway/admin/sync/signal")

    @PostMapping("/import/smartolt")
    fun importFromSmartOlt(request: HttpServletRequest): ResponseEntity<String> =
        post("/api/olt-gateway/admin/import/smartolt", forwardedQuery(request))

    private fun forwardedQuery(request: HttpServletRequest): String? {
        val raw = request.queryString
        if (!raw.isNullOrBlank()) return raw
        val names = request.parameterNames ?: return null
        val parts = names.toList().flatMap { name ->
            request.getParameterValues(name).orEmpty().map { value -> "$name=$value" }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("&")
    }

    private fun proxy(path: String, query: String?): ResponseEntity<String> {
        return try {
            val upstream = oltGatewayHttpClient.getJson(path, query)
            ResponseEntity.status(upstream.statusCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(upstream.body ?: "{}")
        } catch (ex: org.springframework.web.client.RestClientResponseException) {
            throw com.dscorp.wispadmin.wispadmin.util.SubsystemHttpErrors.translate(ex, "olt-gateway")
        } catch (ex: com.dscorp.wispadmin.transport.InvalidSubsystemResponse) {
            throw com.dscorp.wispadmin.wispadmin.util.SubsystemHttpErrors.translate(ex, "olt-gateway")
        } catch (ex: RestClientException) {
            throw com.dscorp.wispadmin.wispadmin.util.SubsystemHttpErrors.translate(ex, "olt-gateway")
        } catch (ex: IllegalArgumentException) {
            throw com.dscorp.wispadmin.wispadmin.util.SubsystemFailure("olt-gateway","UPSTREAM_UNAVAILABLE",HttpStatus.SERVICE_UNAVAILABLE)
        }
    }

    private fun post(path: String, query: String? = null): ResponseEntity<String> {
        return try {
            val upstream = oltGatewayHttpClient.postJson(path, query)
            ResponseEntity.status(upstream.statusCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(upstream.body ?: "{}")
        } catch (ex: org.springframework.web.client.RestClientResponseException) {
            throw com.dscorp.wispadmin.wispadmin.util.SubsystemHttpErrors.translate(ex, "olt-gateway")
        } catch (ex: com.dscorp.wispadmin.transport.InvalidSubsystemResponse) {
            throw com.dscorp.wispadmin.wispadmin.util.SubsystemHttpErrors.translate(ex, "olt-gateway")
        } catch (ex: RestClientException) {
            throw com.dscorp.wispadmin.wispadmin.util.SubsystemHttpErrors.translate(ex, "olt-gateway")
        } catch (ex: IllegalArgumentException) {
            throw com.dscorp.wispadmin.wispadmin.util.SubsystemFailure("olt-gateway","UPSTREAM_UNAVAILABLE",HttpStatus.SERVICE_UNAVAILABLE)
        }
    }
}
