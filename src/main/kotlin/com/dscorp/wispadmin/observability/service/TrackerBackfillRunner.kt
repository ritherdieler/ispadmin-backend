package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.repository.ObsIssueRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(50)
class TrackerBackfillRunner(
    private val issueRepository: ObsIssueRepository,
    private val properties: ObservabilityProperties
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun run(args: ApplicationArguments) {
        val pending = issueRepository.findByTrackerIssueKeyIsNullAndJiraIssueKeyIsNotNull()
        if (pending.isEmpty()) return

        val baseUrl = properties.resolvedJira().baseUrl.trimEnd('/')
        pending.forEach { issue ->
            val key = issue.jiraIssueKey
            issue.trackerProvider = "jira"
            issue.trackerIssueKey = key
            issue.trackerBrowseUrl = if (baseUrl.isNotBlank() && !key.isNullOrBlank()) "$baseUrl/browse/$key" else null
        }
        issueRepository.saveAll(pending)
        log.info("Backfill de tracker: {} issues migrados de jira_issue_key a tracker_issue_key", pending.size)
    }
}
