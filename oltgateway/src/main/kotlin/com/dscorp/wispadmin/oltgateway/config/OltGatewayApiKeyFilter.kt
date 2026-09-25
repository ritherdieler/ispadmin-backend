package com.dscorp.wispadmin.oltgateway.config

import com.dscorp.wispadmin.events.EventRouteContext
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import javax.servlet.FilterChain
import javax.servlet.ReadListener
import javax.servlet.ServletInputStream
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletRequestWrapper
import javax.servlet.http.HttpServletResponse

@Order(Ordered.HIGHEST_PRECEDENCE + 25)
class OltGatewayApiKeyFilter(
    private val properties: OltGatewayProperties,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    companion object {
        const val HEADER = "X-Olt-Gateway-Key"
        const val SMARTOLT_TOKEN_HEADER = "X-Token"
        const val ACS_TO_GATEWAY_HEADER = "X-Acs-To-Gateway-Key"
        const val ENV_HEADER = "X-Gigafiber-Env"
        private const val MAX_SN_BODY = 65_536
        private val pathSerial = Regex("""/(?:onu/(?:move|delete|reboot)|onus)/([^/]+)""")
        private val jsonSerial = Regex(""""sn"\s*:\s*"([^"]+)"""")

        internal fun gatewayPath(request: HttpServletRequest): String {
            val servletPath = request.servletPath?.takeIf { it.isNotBlank() }
            val contextPath = request.contextPath.orEmpty()
            val fromUri = request.requestURI
                ?.takeIf { it.isNotBlank() }
                ?.let { uri ->
                    if (contextPath.isNotEmpty() && uri.startsWith(contextPath)) {
                        uri.removePrefix(contextPath).ifBlank { "/" }
                    } else {
                        uri
                    }
                }
            return (servletPath ?: fromUri ?: "").trimEnd('/')
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if ("OPTIONS".equals(request.method, ignoreCase = true)) return true
        val path = gatewayPath(request)
        if (path.endsWith("/api/olt-gateway/health")) return true
        return !isGatewayPath(request)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = gatewayPath(request)
        val key = request.getHeader(HEADER)
            ?: request.getHeader(SMARTOLT_TOKEN_HEADER)
            ?: request.getHeader(ACS_TO_GATEWAY_HEADER)
        val valid = if (path.endsWith("/api/olt-gateway/acs/cpe-inform")) {
            properties.isValidIngestApiKey(key)
        } else {
            properties.isValidApiKey(key)
        }
        if (!valid) {
            reject(response, HttpStatus.UNAUTHORIZED, "unauthorized", "Missing or invalid $HEADER (or $SMARTOLT_TOKEN_HEADER) header")
            return
        }
        val caller = properties.callerFor(key)
        val env = request.getHeader(ENV_HEADER)
        if (caller != null && !env.isNullOrBlank() && !caller.matchesEnv(env)) {
            reject(response, HttpStatus.BAD_REQUEST, "caller_mismatch", "X-Gigafiber-Env does not match the API key")
            return
        }
        val next = if (caller == GatewayCaller.STAGING && isOltWrite(request)) {
            val guarded = guardLabWrite(request, response) ?: return
            guarded
        } else {
            request
        }
        try {
            caller?.let { EventRouteContext.setNamespace(it.redisNamespace()) }
            GatewayCallContext.setEnv(env)
            GatewayCallContext.setLabInventoryOnly(caller == GatewayCaller.STAGING)
            filterChain.doFilter(next, response)
        } finally {
            EventRouteContext.clear()
            GatewayCallContext.clear()
        }
    }

    private fun guardLabWrite(request: HttpServletRequest, response: HttpServletResponse): HttpServletRequest? {
        val (sn, next) = serialAndRequest(request)
        if (!properties.isLabSerial(sn)) {
            reject(response, HttpStatus.FORBIDDEN, "lab_sn_required", "Staging writes are limited to lab ONU serials")
            return null
        }
        return next
    }

    private fun isOltWrite(request: HttpServletRequest): Boolean {
        val method = request.method.orEmpty()
        if (method.equals("GET", true) || method.equals("HEAD", true)) return false
        val path = gatewayPath(request)
        if (path.endsWith("/api/olt-gateway/acs/cpe-inform")) return false
        return !path.contains("/onu/lab")
    }

    private fun serialAndRequest(request: HttpServletRequest): Pair<String?, HttpServletRequest> {
        pathSerial.find(gatewayPath(request))?.groupValues?.getOrNull(1)?.let { return it to request }
        if (isJson(request)) {
            val bytes = request.inputStream.readNBytes(MAX_SN_BODY)
            return jsonSerial.find(bytes.toString(Charsets.UTF_8))?.groupValues?.getOrNull(1) to ReplayRequest(request, bytes)
        }
        return request.getParameter("sn")?.takeIf { it.isNotBlank() } to request
    }

    private fun isJson(request: HttpServletRequest): Boolean =
        request.contentType?.contains("json", ignoreCase = true) == true

    private fun isGatewayPath(request: HttpServletRequest): Boolean {
        val path = gatewayPath(request)
        val uri = request.requestURI ?: ""
        return path.startsWith("/api/olt-gateway") ||
            path.contains("/api/olt-gateway/") ||
            uri.contains("/api/olt-gateway/")
    }

    private fun reject(response: HttpServletResponse, status: HttpStatus, error: String, message: String) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(objectMapper.writeValueAsString(mapOf("error" to error, "message" to message)))
    }

    private class ReplayRequest(
        request: HttpServletRequest,
        private val cached: ByteArray,
    ) : HttpServletRequestWrapper(request) {
        override fun getInputStream(): ServletInputStream {
            val source = ByteArrayInputStream(cached)
            return object : ServletInputStream() {
                override fun read(): Int = source.read()
                override fun isFinished(): Boolean = source.available() == 0
                override fun isReady(): Boolean = true
                override fun setReadListener(listener: ReadListener?) = Unit
            }
        }

        override fun getReader(): BufferedReader =
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
    }
}
