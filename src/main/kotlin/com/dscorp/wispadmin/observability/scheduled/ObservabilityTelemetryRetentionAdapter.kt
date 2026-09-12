package com.dscorp.wispadmin.observability.scheduled

import com.dscorp.wispadmin.shared.telemetry.TelemetryRetentionPort
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ObservabilityTelemetryRetentionAdapter(
    private val scheduler: ObsRetentionScheduler,
) : TelemetryRetentionPort {
    override fun name() = "observability"
    override fun purgeExpired(now: Instant): Int {
        scheduler.purgeExpiredData()
        return 0
    }
}
