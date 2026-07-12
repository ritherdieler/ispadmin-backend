package com.dscorp.wispadmin.observability.adapter.jira

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.entity.ObsIssueStatus
import com.dscorp.wispadmin.observability.port.CreateTicketRequest
import com.dscorp.wispadmin.observability.port.CreateTicketResult
import com.dscorp.wispadmin.observability.port.IncomingTrackerEvent
import com.dscorp.wispadmin.observability.port.IssueTrackerPort
import com.dscorp.wispadmin.observability.port.TrackerEventType
import com.dscorp.wispadmin.observability.port.TrackerTestResult
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.util.Base64

@Component
class JiraIssueTrackerAdapter(
    private val properties: ObservabilityProperties,
    private val objectMapper: ObjectMapper,
    tracingInterceptor: com.dscorp.wispadmin.observability.tracing.TracingClientHttpRequestInterceptor
) : IssueTrackerPort {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val restTemplate = RestTemplate().apply { interceptors.add(tracingInterceptor) }

    override val providerId: String = "jira"
    override val providerLabel: String = "Jira"

    private val defaultStatusMapping: Map<String, ObsIssueStatus> = mapOf(
        "listo" to ObsIssueStatus.RESOLVED,
        "done" to ObsIssueStatus.RESOLVED,
        "finalizada" to ObsIssueStatus.RESOLVED,
        "finalizado" to ObsIssueStatus.RESOLVED,
        "resuelto" to ObsIssueStatus.RESOLVED,
        "resuelta" to ObsIssueStatus.RESOLVED,
        "cancelado" to ObsIssueStatus.IGNORED,
        "cancelada" to ObsIssueStatus.IGNORED,
        "cancelled" to ObsIssueStatus.IGNORED,
        "canceled" to ObsIssueStatus.IGNORED,
        "tareas por hacer" to ObsIssueStatus.OPEN,
        "to do" to ObsIssueStatus.OPEN,
        "por hacer" to ObsIssueStatus.OPEN,
        "en curso" to ObsIssueStatus.OPEN,
        "in progress" to ObsIssueStatus.OPEN,
        "reabierto" to ObsIssueStatus.OPEN,
        "reopened" to ObsIssueStatus.OPEN
    )

    override fun isConfigured(): Boolean = properties.resolvedJira().isConfigured()

    override fun testConnection(): TrackerTestResult {
        val jira = properties.resolvedJira()
        if (!jira.isConfigured()) {
            return TrackerTestResult(false, "Jira no está configurado (revise observability.tracker.jira.* u observability.jira.*)", null)
        }
        warnIfLegacy(jira)
        return try {
            val headers = authHeaders(jira)
            val url = joinUrl(jira.baseUrl, "/rest/api/3/myself")
            val response = restTemplate.exchange(url, HttpMethod.GET, HttpEntity<Void>(headers), JsonNode::class.java)
            val displayName = response.body?.get("displayName")?.asText()
            TrackerTestResult(true, "Conexión correcta", displayName)
        } catch (e: HttpStatusCodeException) {
            TrackerTestResult(false, "Error ${e.rawStatusCode}: ${e.responseBodyAsString.take(300)}", null)
        } catch (e: Exception) {
            TrackerTestResult(false, "Error de conexión: ${e.message}", null)
        }
    }

    override fun createTicket(request: CreateTicketRequest): CreateTicketResult {
        val jira = properties.resolvedJira()
        if (!jira.isConfigured()) {
            return CreateTicketResult(false, null, null, "Jira no está configurado (revise observability.tracker.jira.* u observability.jira.*)")
        }
        warnIfLegacy(jira)
        return try {
            val headers = authHeaders(jira)
            headers.contentType = MediaType.APPLICATION_JSON

            val fields = HashMap<String, Any>()
            fields["project"] = mapOf("key" to jira.projectKey)
            fields["summary"] = request.summary.take(240)
            fields["issuetype"] = mapOf("name" to jira.issueType)
            fields["description"] = buildAdf(request.descriptionText)
            priorityFor(jira, request.severity)?.let { fields["priority"] = mapOf("name" to it) }

            val body = mapOf("fields" to fields)
            val url = joinUrl(jira.baseUrl, "/rest/api/3/issue")
            val response = restTemplate.exchange(url, HttpMethod.POST, HttpEntity(body, headers), JsonNode::class.java)
            val key = response.body?.get("key")?.asText()
            val browseUrl = key?.let { joinUrl(jira.baseUrl, "/browse/$it") }
            CreateTicketResult(true, key, browseUrl, null)
        } catch (e: HttpStatusCodeException) {
            log.warn("Error creando issue en Jira: {} {}", e.rawStatusCode, e.responseBodyAsString)
            CreateTicketResult(false, null, null, "Error ${e.rawStatusCode}: ${e.responseBodyAsString.take(300)}")
        } catch (e: Exception) {
            log.warn("Error creando issue en Jira: {}", e.message)
            CreateTicketResult(false, null, null, "Error de conexión: ${e.message}")
        }
    }

    override fun parseWebhook(headers: Map<String, String>, rawBody: String): IncomingTrackerEvent? {
        if (rawBody.isBlank()) return null
        val node = try {
            objectMapper.readTree(rawBody)
        } catch (e: Exception) {
            log.warn("Payload de webhook Jira no es JSON válido: {}", e.message)
            return null
        }

        val eventStr = (node.get("event")?.asText() ?: node.get("webhookEvent")?.asText())?.lowercase() ?: return null
        val issueNode = node.get("issue")
        val key = node.get("key")?.asText()
            ?: issueNode?.get("key")?.asText()
        val rawStatus = node.get("status")?.asText()
            ?: issueNode?.get("fields")?.get("status")?.get("name")?.asText()

        val type = when {
            eventStr.contains("delet") -> TrackerEventType.TICKET_DELETED
            eventStr.contains("updat") || eventStr.contains("transition") -> TrackerEventType.TICKET_STATUS_CHANGED
            else -> return null
        }

        val mapped = if (type == TrackerEventType.TICKET_STATUS_CHANGED) mapStatus(rawStatus) else null
        return IncomingTrackerEvent(
            provider = providerId,
            type = type,
            ticketKey = key,
            rawStatus = rawStatus,
            mappedStatus = mapped
        )
    }

    private fun mapStatus(rawStatus: String?): ObsIssueStatus? {
        val raw = rawStatus?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        val configured = properties.resolvedJira().statusMapping
            .entries.firstOrNull { it.key.trim().lowercase() == raw }?.value
        if (configured != null) {
            return runCatching { ObsIssueStatus.valueOf(configured.trim().uppercase()) }.getOrNull()
        }
        return defaultStatusMapping[raw]
    }

    private fun buildAdf(descriptionText: String): Map<String, Any> {
        val paragraphs = descriptionText.split("\n").map { line ->
            mapOf(
                "type" to "paragraph",
                "content" to listOf(
                    mapOf("type" to "text", "text" to line.ifBlank { " " })
                )
            )
        }
        return mapOf(
            "type" to "doc",
            "version" to 1,
            "content" to paragraphs
        )
    }

    private fun priorityFor(jira: com.dscorp.wispadmin.observability.config.ResolvedJiraConfig, severity: String?): String? {
        if (severity.isNullOrBlank()) return null
        return jira.priorityBySeverity[severity.lowercase()]
    }

    private fun authHeaders(jira: com.dscorp.wispadmin.observability.config.ResolvedJiraConfig): HttpHeaders {
        val headers = HttpHeaders()
        val token = "${jira.email}:${jira.apiToken}"
        val encoded = Base64.getEncoder().encodeToString(token.toByteArray(StandardCharsets.UTF_8))
        headers.set(HttpHeaders.AUTHORIZATION, "Basic $encoded")
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        return headers
    }

    private fun warnIfLegacy(jira: com.dscorp.wispadmin.observability.config.ResolvedJiraConfig) {
        if (jira.usingLegacy) {
            log.warn("observability.jira.* está deprecado; migre a observability.tracker.jira.* (proveedor de issue tracker)")
        }
    }

    private fun joinUrl(base: String, path: String): String {
        val trimmedBase = base.trimEnd('/')
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return "$trimmedBase$normalizedPath"
    }
}
