package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.port.IssueTrackerPort
import org.springframework.stereotype.Service

@Service
class IssueTrackerRegistry(
    private val adapters: List<IssueTrackerPort>,
    private val properties: ObservabilityProperties
) {

    fun active(): IssueTrackerPort? {
        val id = properties.activeTrackerProvider()
        if (id.isBlank()) return null
        return adapters.firstOrNull { it.providerId.equals(id, ignoreCase = true) }
    }

    fun byProvider(id: String): IssueTrackerPort? =
        adapters.firstOrNull { it.providerId.equals(id, ignoreCase = true) }
}
