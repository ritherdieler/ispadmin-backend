package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "crm.csat")
class CrmCsatProperties {
    var enabled: Boolean = true
    var templateName: String = "csat_survey_v1"
    var templateLanguage: String = "es_PE"
    var expireHours: Long = 72
    var maxRetries: Int = 3
    var retryMinutes: Long = 60
    var delayMinutes: Long = 5
    var lowScoreThreshold: Int = 2
    var commentWindowMinutes: Long = 30
}
