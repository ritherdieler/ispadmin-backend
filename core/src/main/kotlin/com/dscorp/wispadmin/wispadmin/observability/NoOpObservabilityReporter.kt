package com.dscorp.wispadmin.wispadmin.observability

import org.springframework.stereotype.Component

@Component
class NoOpObservabilityReporter : ObservabilityReporter {
    override fun report(event: ReportedEvent) {
    }
}
