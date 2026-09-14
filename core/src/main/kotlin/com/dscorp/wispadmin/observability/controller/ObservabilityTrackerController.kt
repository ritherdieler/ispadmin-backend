package com.dscorp.wispadmin.observability.controller

import com.dscorp.wispadmin.observability.service.IssueTrackerRegistry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/observability/tracker")
class ObservabilityTrackerController(
    private val registry: IssueTrackerRegistry
) {

    @GetMapping("/info")
    fun info(): Map<String, Any?> {
        val active = registry.active()
        return mapOf(
            "provider" to active?.providerId,
            "label" to (active?.providerLabel ?: "Ticket"),
            "configured" to (active?.isConfigured() ?: false)
        )
    }
}
