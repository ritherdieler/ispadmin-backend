package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.port.IncomingTrackerEvent
import com.dscorp.wispadmin.observability.port.TrackerEventType
import com.dscorp.wispadmin.observability.repository.ObsIssueRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ObsTrackerWebhookService(
    private val issueRepository: ObsIssueRepository
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    fun apply(event: IncomingTrackerEvent): Boolean {
        val key = event.ticketKey?.takeIf { it.isNotBlank() } ?: return false
        val issue = issueRepository.findByTrackerIssueKey(key)
            ?: issueRepository.findByJiraIssueKey(key)
            ?: return false

        when (event.type) {
            TrackerEventType.TICKET_DELETED -> {
                issue.trackerProvider = null
                issue.trackerIssueKey = null
                issue.trackerBrowseUrl = null
                issue.jiraIssueKey = null
            }
            TrackerEventType.TICKET_STATUS_CHANGED -> {
                val mapped = event.mappedStatus ?: run {
                    log.info("Webhook tracker: estado '{}' de {} sin mapeo; se ignora", event.rawStatus, key)
                    return false
                }
                issue.status = mapped
            }
        }
        issueRepository.save(issue)
        return true
    }
}
