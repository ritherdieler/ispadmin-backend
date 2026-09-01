package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.wispadmin.observability.ObservabilityReporter
import com.dscorp.wispadmin.wispadmin.observability.ReportedEvent
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Component
@Primary
class InProcessObservabilityReporter(
    private val ingestionService: ObsIngestionService,
    private val properties: ObservabilityProperties
) : ObservabilityReporter {

    override fun report(event: ReportedEvent) {
        if (!properties.enabled || !properties.internalReportEnabled) return
        ingestionService.ingestAsync(event)
    }
}
