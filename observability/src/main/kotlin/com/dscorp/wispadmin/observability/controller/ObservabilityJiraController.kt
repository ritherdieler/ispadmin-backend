package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.port.TrackerTestResult
import com.dscorp.wispadmin.observability.service.IssueTrackerRegistry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/observability/jira")
class ObservabilityJiraController(
    private val registry: IssueTrackerRegistry
) {

    @GetMapping("/status")
    fun status(): Map<String, Any> {
        val tracker = registry.byProvider("jira")
        return mapOf("configured" to (tracker?.isConfigured() ?: false))
    }

    @PostMapping("/test")
    fun test(): TrackerTestResult {
        val tracker = registry.byProvider("jira")
            ?: return TrackerTestResult(false, "Adaptador Jira no disponible", null)
        return tracker.testConnection()
    }
}
