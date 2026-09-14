package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.port.CreateTicketRequest
import com.dscorp.wispadmin.observability.port.CreateTicketResult
import com.dscorp.wispadmin.observability.repository.ObsEventRepository
import com.dscorp.wispadmin.observability.repository.ObsIssueRepository
import org.springframework.stereotype.Service

@Service
class ObsTicketApplicationService(
    private val issueRepository: ObsIssueRepository,
    private val eventRepository: ObsEventRepository,
    private val registry: IssueTrackerRegistry,
    private val properties: ObservabilityProperties
) {

    fun createTicketForIssue(issueId: Long, override: TicketOverride?): CreateTicketResult {
        val tracker = registry.active()
            ?: return CreateTicketResult(false, null, null, "No hay issue tracker configurado")

        val issue = issueRepository.findById(issueId).orElse(null)
            ?: return CreateTicketResult(false, null, null, "Issue no encontrado")

        if (!issue.trackerIssueKey.isNullOrBlank()) {
            return CreateTicketResult(true, issue.trackerIssueKey, issue.trackerBrowseUrl, "El issue ya tiene un ticket asociado")
        }

        override?.summary?.takeIf { it.isNotBlank() }?.let { issue.title = it }

        val summary = issue.title ?: issue.errorType ?: issue.fingerprint
        val descriptionText = buildDescription(issue, override)

        val request = CreateTicketRequest(
            summary = summary,
            descriptionText = descriptionText,
            severity = issue.severity
        )

        val result = tracker.createTicket(request)
        if (result.ok && !result.issueKey.isNullOrBlank()) {
            issue.trackerProvider = tracker.providerId
            issue.trackerIssueKey = result.issueKey
            issue.trackerBrowseUrl = result.browseUrl
            if (tracker.providerId == "jira") issue.jiraIssueKey = result.issueKey
            issueRepository.save(issue)
        }
        return result
    }

    fun unlink(issueId: Long): Boolean {
        val issue = issueRepository.findById(issueId).orElse(null) ?: return false
        issue.trackerProvider = null
        issue.trackerIssueKey = null
        issue.trackerBrowseUrl = null
        issue.jiraIssueKey = null
        issueRepository.save(issue)
        return true
    }

    private fun buildDescription(
        issue: com.dscorp.wispadmin.observability.entity.ObsIssue,
        override: TicketOverride?
    ): String {
        val sb = StringBuilder()
        override?.description?.takeIf { it.isNotBlank() }?.let {
            sb.append(it).append("\n\n")
        }
        sb.append("Fingerprint: ").append(issue.fingerprint).append("\n")
        sb.append("Plataforma: ").append(issue.platform).append("\n")
        sb.append("Severidad: ").append(issue.severity).append("\n")
        sb.append("Estado: ").append(issue.status).append("\n")
        sb.append("Ocurrencias: ").append(issue.eventCount).append("\n")
        sb.append("Primera vez: ").append(issue.firstSeen).append("\n")
        sb.append("Última vez: ").append(issue.lastSeen).append("\n")
        sb.append("Entorno: ").append(issue.lastEnvironment).append("\n")
        sb.append("Release: ").append(issue.lastRelease).append("\n")

        val dashboardBaseUrl = properties.resolvedJira().dashboardBaseUrl
        if (dashboardBaseUrl.isNotBlank() && issue.id != null) {
            sb.append("Dashboard: ").append(dashboardBaseUrl.trimEnd('/')).append("/issues/").append(issue.id).append("\n")
        }

        sb.append("\n").append("Mensaje:").append("\n")
        sb.append(issue.lastMessage ?: "").append("\n")

        val latestEvent = eventRepository.findFirstByIssueIdOrderByCreatedAtDesc(issue.id ?: -1)
        latestEvent?.let {
            sb.append("\n")
            sb.append("Correlation-Id: ").append(it.correlationId ?: "-").append("\n")
            sb.append("URL: ").append(it.url ?: "-").append("\n")
            sb.append("HTTP: ").append(it.httpMethod ?: "-").append(" ").append(it.httpStatus ?: "-").append("\n")
            sb.append("User-Agent: ").append(it.userAgent ?: "-").append("\n\n")
            sb.append("Stacktrace:").append("\n").append((it.stacktrace ?: "-").take(4000))
        }
        return sb.toString()
    }
}

data class TicketOverride(
    val summary: String? = null,
    val description: String? = null
)
