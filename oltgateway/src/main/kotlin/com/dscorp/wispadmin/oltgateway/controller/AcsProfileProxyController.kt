package com.dscorp.wispadmin.oltgateway.controller

import com.dscorp.wispadmin.oltgateway.client.AcsCpeClient
import com.dscorp.wispadmin.oltgateway.config.OltGatewayOpenApi
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/olt-gateway/acs/profiles")
@ConditionalOnProperty(prefix = "olt.gateway", name = ["enabled"], havingValue = "true")
@Tag(name = "OLT Gateway ACS profiles", description = "Proxy TR-069 profiles to ACS WAR")
@SecurityRequirement(name = OltGatewayOpenApi.SECURITY_SCHEME)
class AcsProfileProxyController(
    private val acs: AcsCpeClient,
) {
    @GetMapping
    fun list(): ResponseEntity<String> = forward(acs.listProfiles())

    @PostMapping("/preview")
    fun preview(@RequestBody body: String): ResponseEntity<String> = forward(acs.previewProfile(body))

    @PostMapping("/import")
    fun importProfile(@RequestBody body: String): ResponseEntity<String> = forward(acs.importProfile(body))

    @DeleteMapping("/{productClass}")
    fun delete(@PathVariable productClass: String): ResponseEntity<String> = forward(acs.deleteProfile(productClass))

    private fun forward(upstream: ResponseEntity<String>): ResponseEntity<String> {
        val builder = ResponseEntity.status(upstream.statusCode)
        if (upstream.statusCode.value() != 204) {
            builder.contentType(MediaType.APPLICATION_JSON)
        }
        return builder.body(upstream.body)
    }
}
