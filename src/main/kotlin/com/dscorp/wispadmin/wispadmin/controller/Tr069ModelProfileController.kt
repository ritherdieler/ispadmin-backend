package com.dscorp.wispadmin.wispadmin.controller

import com.dscorp.wispadmin.wispadmin.oltclient.OltGatewayHttpClient
import com.dscorp.wispadmin.wispadmin.security.CrmAccessPolicy
import com.dscorp.wispadmin.wispadmin.security.PlatformAuthFilter
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClientException
import org.springframework.web.multipart.MultipartFile
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/admin/tr069-profiles")
@ConditionalOnProperty(prefix = "olt.gateway", name = ["client-enabled"], havingValue = "true")
class Tr069ModelProfileController(
    private val oltGatewayHttpClient: OltGatewayHttpClient,
    private val objectMapper: ObjectMapper,
) {

    @GetMapping
    fun list(httpRequest: HttpServletRequest): ResponseEntity<Any> {
        if (!isAdmin(httpRequest)) return forbidden()
        return proxy { oltGatewayHttpClient.getJson("/api/olt-gateway/acs/profiles") }
    }

    @PostMapping("/preview")
    fun preview(
        @RequestParam("file") file: MultipartFile,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<Any> {
        if (!isAdmin(httpRequest)) return forbidden()
        return proxy {
            oltGatewayHttpClient.postJsonBody(
                "/api/olt-gateway/acs/profiles/preview",
                objectMapper.writeValueAsString(mapOf("csv" to readCsv(file))),
            )
        }
    }

    @PostMapping("/import")
    fun importProfile(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("aliases", required = false) aliases: String?,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<Any> {
        if (!isAdmin(httpRequest)) return forbidden()
        val aliasList = aliases?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        return proxy {
            oltGatewayHttpClient.postJsonBody(
                "/api/olt-gateway/acs/profiles/import",
                objectMapper.writeValueAsString(
                    mapOf(
                        "csv" to readCsv(file),
                        "aliases" to aliasList,
                        "importedBy" to resolveUsername(httpRequest),
                    ),
                ),
            )
        }
    }

    @DeleteMapping("/{productClass}")
    fun delete(
        @PathVariable productClass: String,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<Any> {
        if (!isAdmin(httpRequest)) return forbidden()
        val encoded = java.net.URLEncoder.encode(productClass, Charsets.UTF_8).replace("+", "%20")
        return proxy { oltGatewayHttpClient.deleteJson("/api/olt-gateway/acs/profiles/$encoded") }
    }

    private fun proxy(call: () -> ResponseEntity<String>): ResponseEntity<Any> {
        return try {
            val upstream = call()
            if (upstream.statusCode == HttpStatus.NO_CONTENT) {
                ResponseEntity.noContent().build()
            } else {
                ResponseEntity.status(upstream.statusCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(upstream.body ?: "{}")
            }
        } catch (ex: org.springframework.web.client.RestClientResponseException) {
            if (ex.rawStatusCode in 400..499) {
                ResponseEntity.status(ex.rawStatusCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.responseBodyAsString ?: """{"error":"ACS error"}""")
            } else {
                throw com.dscorp.wispadmin.wispadmin.util.SubsystemHttpErrors.translate(ex, "olt-gateway")
            }
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

    private fun readCsv(file: MultipartFile): String {
        if (file.isEmpty) error("El archivo CSV está vacío")
        return file.inputStream.bufferedReader().readText()
    }

    private fun isAdmin(request: HttpServletRequest): Boolean =
        CrmAccessPolicy.canManageCrmSecrets(
            request.getAttribute(PlatformAuthFilter.AUTH_USER_TYPE_ATTRIBUTE)?.toString(),
        )

    private fun resolveUsername(request: HttpServletRequest): String? =
        request.getAttribute(PlatformAuthFilter.AUTH_USERNAME_ATTRIBUTE)?.toString()

    private fun forbidden(): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(mapOf("error" to "Solo ADMIN puede gestionar perfiles TR-069"))
}
