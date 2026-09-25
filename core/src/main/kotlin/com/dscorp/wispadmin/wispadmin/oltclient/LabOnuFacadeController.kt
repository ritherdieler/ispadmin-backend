package com.dscorp.wispadmin.wispadmin.oltclient

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/onu/lab")
@ConditionalOnExpression(
    "\${olt.gateway.client-enabled:false} && '\${gigafiber.environment.tag:}' == 'stg'",
)
class LabOnuFacadeController(
    private val oltGatewayHttpClient: OltGatewayHttpClient,
) {
    @GetMapping
    fun list(): ResponseEntity<String> = proxy { oltGatewayHttpClient.getJson("/api/olt-gateway/onu/lab") }

    @PostMapping
    fun add(@RequestBody body: String): ResponseEntity<String> =
        proxy { oltGatewayHttpClient.postJsonBody("/api/olt-gateway/onu/lab", body) }

    @DeleteMapping("/{sn}")
    fun remove(@PathVariable sn: String): ResponseEntity<String> {
        val encoded = UriUtils.encodePathSegment(sn, StandardCharsets.UTF_8)
        return proxy { oltGatewayHttpClient.deleteJson("/api/olt-gateway/onu/lab/$encoded") }
    }

    private fun proxy(call: () -> ResponseEntity<String>): ResponseEntity<String> {
        return try {
            val upstream = call()
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
            throw com.dscorp.wispadmin.wispadmin.util.SubsystemFailure(
                "olt-gateway",
                "UPSTREAM_UNAVAILABLE",
                HttpStatus.SERVICE_UNAVAILABLE,
            )
        }
    }
}
