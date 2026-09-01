package com.dscorp.wispadmin.wispadmin.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "observability.session")
class SessionTokenProperties {
    var secret: String = ""
    var ttlMinutes: Long = 720
    var refreshTtlMinutes: Long = 43200
}
