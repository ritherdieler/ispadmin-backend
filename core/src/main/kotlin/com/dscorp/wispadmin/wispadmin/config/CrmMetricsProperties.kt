package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "crm.metrics")
class CrmMetricsProperties {
    var firstResponseSlaMinutes: Long = 15
    var resolutionSlaMinutes: Long = 240
    var abandonmentPendingHours: Long = 24
    var excessiveWaitMinutes: Long = 30
    var shiftHandoffLookbackHours: Long = 8
}
