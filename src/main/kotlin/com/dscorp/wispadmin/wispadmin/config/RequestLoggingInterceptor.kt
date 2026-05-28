package com.dscorp.wispadmin.wispadmin.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.dscorp.wispadmin.wispadmin.logging.StackTraceSummarizer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.WebUtils
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@Component
class RequestLoggingInterceptor @Autowired constructor(
    private val objectMapper: ObjectMapper
) : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(this::class.java)

    private val excludedPaths = listOf(
        "/api/logs/viewer",
        "/api/logs/files",
        "/api/logs/modules",
        "/api/logs/http-failures",
        "/api/logs/errors",
        "/api/logs/search",
        "/api/logs/db"
    )

    private val ts = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        request.setAttribute("startTime", System.currentTimeMillis())
        request.setAttribute("requestId", UUID.randomUUID().toString())
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        val uri = request.requestURI
        if (shouldSkipLogging(uri)) return

        val startTime = request.getAttribute("startTime") as? Long ?: return
        val requestId = request.getAttribute("requestId") as? String ?: "unknown"
        val duration = System.currentTimeMillis() - startTime
        val status = response.status

        if (status >= 400 || ex != null) {
            emitHttpFailure(request, requestId, status, duration, ex)
        } else {
            log.info("Request [{}] {} {} → {} ({}ms)", requestId, request.method, uri, status, duration)
        }
    }

    private fun emitHttpFailure(
        request: HttpServletRequest,
        requestId: String,
        status: Int,
        duration: Long,
        ex: Exception?
    ) {
        val method = request.method
        val uri = request.requestURI
        val query = request.queryString ?: ""
        val remoteAddr = request.remoteAddr
        val contentType = request.contentType ?: ""
        val userAgent = request.getHeader("User-Agent") ?: ""
        val authorization = request.getHeader("Authorization")
            ?.let { if (it.length > 20) it.substring(0, 20) + "..." else it } ?: ""

        val reqBody = extractRequestBody(request)
        val resBody = request.getAttribute(RequestBodyCachingFilter.ATTR_RESPONSE_BODY) as? String ?: ""
        val errorMsg = ex?.message ?: ""
        val fromAttr = request.getAttribute(HttpFailureContext.ATTR_STACK_SUMMARY) as? String
        request.removeAttribute(HttpFailureContext.ATTR_STACK_SUMMARY)
        val stackSummary = when {
            ex != null -> StackTraceSummarizer.summarize(ex)
            !fromAttr.isNullOrBlank() -> fromAttr
            else -> summarizeStackFromResponseBody(resBody)
        }

        val payload = mapOf(
            "ts" to LocalDateTime.now().format(ts),
            "id" to requestId,
            "method" to method,
            "uri" to uri,
            "query" to query,
            "status" to status,
            "ms" to duration,
            "ip" to remoteAddr,
            "contentType" to contentType,
            "userAgent" to userAgent,
            "authorization" to authorization,
            "reqBody" to reqBody,
            "resBody" to resBody,
            "error" to errorMsg,
            "stackTraceSummary" to stackSummary
        )

        val json = objectMapper.writeValueAsString(payload)
        log.error("[HTTP_FAILURE] {}", json)
    }

    private fun summarizeStackFromResponseBody(resBody: String): String {
        if (resBody.isBlank()) return ""
        val trimmed = resBody.trim()
        if (!trimmed.startsWith("{")) return ""
        return try {
            val node = objectMapper.readTree(trimmed)
            val trace = node.get("trace")?.asText()
                ?: node.get("stackTrace")?.asText()
                ?: return ""
            val lines = trace.lines().filter { it.isNotBlank() }
            val maxLines = 15
            val head = lines.take(maxLines).joinToString("\n")
            if (lines.size > maxLines) head + "\n... (" + (lines.size - maxLines) + " líneas más)"
            else head
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractRequestBody(request: HttpServletRequest): String {
        val cached = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper::class.java)
        if (cached != null && cached.contentAsByteArray.isNotEmpty()) {
            val body = String(cached.contentAsByteArray, StandardCharsets.UTF_8)
            return if (body.length > 5000) body.substring(0, 5000) + "...[truncated]" else body
        }
        return ""
    }

    private fun shouldSkipLogging(uri: String) = excludedPaths.any { uri.contains(it) }
}
