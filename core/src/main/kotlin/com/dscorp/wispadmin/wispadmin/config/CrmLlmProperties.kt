package com.dscorp.wispadmin.wispadmin.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "crm.llm")
class CrmLlmProperties {
    var masterKey: String = ""
    var bootstrapApiKey: String = ""
    var defaultModel: String = "gpt-4o-mini"
    var confidenceThreshold: Double = 0.65
    var timeoutMs: Long = 8000
    var maxUnknownRetries: Int = 2
    var baseUrl: String = "https://api.openai.com/v1"
}
