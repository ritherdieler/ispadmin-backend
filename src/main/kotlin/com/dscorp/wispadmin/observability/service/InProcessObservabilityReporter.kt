package com.dscorp.wispadmin.observability.service

import com.dscorp.wispadmin.observability.config.ObservabilityProperties
import com.dscorp.wispadmin.observability.port.ObservabilityReporter
import com.dscorp.wispadmin.observability.port.ReportedEvent
import org.springframework.stereotype.Component

@Component
class InProcessObservabilityReporter(
    private val ingestionService: ObsIngestionService,
    private val properties: ObservabilityProperties
) : ObservabilityReporter {

    override fun report(event: ReportedEvent) {
        if (!properties.enabled || !properties.internalReportEnabled) return
        ingestionService.ingestAsync(event)
    }
}
